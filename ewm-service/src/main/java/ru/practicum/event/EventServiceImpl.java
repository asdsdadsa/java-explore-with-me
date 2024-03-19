package ru.practicum.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.StatsClient;
import ru.practicum.category.Category;
import ru.practicum.category.CategoryRepository;
import ru.practicum.dto.HitDto;
import ru.practicum.dto.StatsDto;
import ru.practicum.event.dto.*;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.Location;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.exception.ValidationException;
import ru.practicum.request.Request;
import ru.practicum.request.RequestRepository;
import ru.practicum.request.dto.RequestDto;
import ru.practicum.user.User;
import ru.practicum.user.UserRepository;
import ru.practicum.util.enums.State;
import ru.practicum.util.enums.StateAction;
import ru.practicum.util.enums.Status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ru.practicum.Util.FORMATTER;
import static ru.practicum.Util.START_HISTORY;
import static ru.practicum.event.EventMapper.*;
import static ru.practicum.request.RequestMapper.returnRequestDtoList;
import static ru.practicum.util.enums.State.PUBLISHED;

@Slf4j
@Service
@AllArgsConstructor
public class EventServiceImpl implements EventService {
    private final EventRepository eventRepository;
    private final RequestRepository requestRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final StatsClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    @Transactional
    public EventFullDto addEvent(Long userId, EventNewDto eventNewDto) {

        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Пользователя с id " + userId + " не существует."));
        Category category = categoryRepository.findById(eventNewDto.getCategory()).orElseThrow(()
                -> new NotFoundException("Категории с id " + eventNewDto.getCategory() + " не существует."));
        Location location = locationRepository.save(LocationMapper.returnLocation(eventNewDto.getLocation()));
        Event event = EventMapper.returnEvent(eventNewDto, category, location, user);
        eventRepository.save(event);

        return returnEventFullDto(event);
    }

    @Transactional(readOnly = true)
    @Override
    public List<EventShortDto> getAllEventsByUserId(Long userId, Integer from, Integer size) {

        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Пользователя с id " + userId + " не существует."));
        PageRequest pageRequest = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findByInitiatorId(userId, pageRequest);

        return returnEventShortDtoList(events);
    }

    @Transactional(readOnly = true)
    @Override
    public EventFullDto getUserEventById(Long userId, Long eventId) {

        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Пользователя с id " + userId + " не существует."));
        eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("События с id " + eventId + " не существует."));
        Event event = eventRepository.findByInitiatorIdAndId(userId, eventId);

        return returnEventFullDto(event);
    }

    @Override
    @Transactional
    public EventFullDto updateEventByUserId(EventUpdateDto eventUpdateDto, Long userId, Long eventId) {

        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Пользователя с id " + userId + " не существует."));
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("События с id " + eventId + " не существует."));

        if (!user.getId().equals(event.getInitiator().getId())) {
            throw new ConflictException("User не является инициатором события.");
        }
        if (event.getState().equals(PUBLISHED)) {
            throw new ConflictException("User не может обновить уже опубликованное событие.");
        }

        Event updateEvent = baseUpdateEvent(event, eventUpdateDto);

        return returnEventFullDto(updateEvent);
    }

    @Transactional(readOnly = true)
    @Override
    public List<RequestDto> getRequestsForEventIdByUserId(Long userId, Long eventId) {

        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Пользователя с id " + userId + " не существует."));
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("События с id " + eventId + " не существует."));

        if (!user.getId().equals(event.getInitiator().getId())) {
            throw new ConflictException("User не является инициатором события.");
        }

        List<Request> requests = requestRepository.findByEventId(eventId);

        return returnRequestDtoList(requests);
    }

    @Override
    @Transactional
    public RequestUpdateDtoResult updateStatusRequestsForEventIdByUserId(RequestUpdateDtoRequest requestDto, Long userId, Long eventId) {

        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Пользователя с id " + userId + " не существует."));
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("События с id " + eventId + " не существует."));

        RequestUpdateDtoResult result = RequestUpdateDtoResult.builder()
                .confirmedRequests(Collections.emptyList())
                .rejectedRequests(Collections.emptyList())
                .build();

        if (!user.getId().equals(event.getInitiator().getId())) {
            throw new ConflictException("User не является инициатором события.");
        }

        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            return result;
        }

        if (event.getConfirmedRequests() >= event.getParticipantLimit()) {
            throw new ConflictException("Превышен лимит участников.");
        }

        List<Request> confirmedRequests = new ArrayList<>();
        List<Request> rejectedRequests = new ArrayList<>();

        long vacantPlace = event.getParticipantLimit() - event.getConfirmedRequests();

        List<Request> requestsList = requestRepository.findAllById(requestDto.getRequestIds());

        for (Request request : requestsList) {
            if (!request.getStatus().equals(Status.PENDING)) {
                throw new ConflictException("Request должен иметь статус PENDING.");
            }

            if (requestDto.getStatus().equals(Status.CONFIRMED) && vacantPlace > 0) {
                request.setStatus(Status.CONFIRMED);
                event.setConfirmedRequests(requestRepository.countAllByEventIdAndStatus(eventId, Status.CONFIRMED));
                confirmedRequests.add(request);
                vacantPlace--;
            } else {
                request.setStatus(Status.REJECTED);
                rejectedRequests.add(request);
            }
        }

        result.setConfirmedRequests(returnRequestDtoList(confirmedRequests));
        result.setRejectedRequests(returnRequestDtoList(rejectedRequests));

        eventRepository.save(event);
        requestRepository.saveAll(requestsList);

        return result;
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(EventUpdateDto eventUpdateDto, Long eventId) {

        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("События с id " + eventId + " не существует."));

        if (eventUpdateDto.getStateAction() != null) {
            if (eventUpdateDto.getStateAction().equals(StateAction.PUBLISH_EVENT)) {

                if (!event.getState().equals(State.PENDING)) {
                    throw new ConflictException("Event уже опубликован, повторно опубликовать нельзя.");
                }
                event.setPublishedOn(LocalDateTime.now());
                event.setState(State.PUBLISHED);

            } else {

                if (!event.getState().equals(State.PENDING)) {
                    throw new ConflictException("Event не может быть отменен, поскольку его статус не PENDING.");
                }
                event.setState(State.CANCELED);
            }
        }

        Event updateEvent = baseUpdateEvent(event, eventUpdateDto);

        return returnEventFullDto(updateEvent);
    }

    @Transactional(readOnly = true)
    @Override
    public List<EventFullDto> getEventsByAdmin(List<Long> users, List<String> states, List<Long> categories, String rangeStart, String rangeEnd, Integer from, Integer size) {

        LocalDateTime startTime = parseDate(rangeStart);
        LocalDateTime endTime = parseDate(rangeEnd);

        List<State> statesValue = new ArrayList<>();

        if (states != null) {
            for (String state : states) {
                statesValue.add(State.getStateValue(state));
            }
        }

        if (startTime != null && endTime != null) {
            if (startTime.isAfter(endTime)) {
                throw new ValidationException("Start должно быть после End.");
            }
        }

        PageRequest pageRequest = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findEventsByAdminFromParam(users, statesValue, categories, startTime, endTime, pageRequest);

        return returnEventFullDtoList(events);
    }

    @Transactional(readOnly = true)
    @Override
    public EventFullDto getEventById(Long eventId, String uri, String ip) {

        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("События с id " + eventId + " не существует."));
        if (!event.getState().equals(PUBLISHED)) {
            throw new NotFoundException("Event не опубликован.");      // !!!!!!
        }

        sendInfo(uri, ip);
        event.setViews(getViewsEventById(event.getId()));
        eventRepository.save(event);

        return returnEventFullDto(event);
    }

    @Transactional(readOnly = true)
    @Override
    public List<EventShortDto> getEventsByPublic(String text, List<Long> categories, Boolean paid, String rangeStart, String rangeEnd, Boolean onlyAvailable, String sort, Integer from, Integer size, String uri, String ip) {

        LocalDateTime startTime = parseDate(rangeStart);
        LocalDateTime endTime = parseDate(rangeEnd);

        if (startTime != null && endTime != null) {
            if (startTime.isAfter(endTime)) {
                throw new ValidationException("Start должно быть после End");
            }
        }

        PageRequest pageRequest = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findEventsByPublicFromParam(text, categories, paid, startTime, endTime, onlyAvailable, sort, pageRequest);

        sendInfo(uri, ip);
        for (Event event : events) {
            event.setViews(getViewsEventById(event.getId()));
            eventRepository.save(event);
        }

        return returnEventShortDtoList(events);
    }

    private Event baseUpdateEvent(Event event, EventUpdateDto eventUpdateDto) {

        if (eventUpdateDto.getAnnotation() != null && !eventUpdateDto.getAnnotation().isBlank()) {
            event.setAnnotation(eventUpdateDto.getAnnotation());
        }
        if (eventUpdateDto.getCategory() != null) {
            event.setCategory(categoryRepository.findById(eventUpdateDto.getCategory()).orElseThrow(()
                    -> new NotFoundException("Категории с id " + eventUpdateDto.getCategory() + " не существует.")));
        }
        if (eventUpdateDto.getDescription() != null && !eventUpdateDto.getDescription().isBlank()) {
            event.setDescription(eventUpdateDto.getDescription());
        }
        if (eventUpdateDto.getEventDate() != null) {
            event.setEventDate(eventUpdateDto.getEventDate());
        }
        if (eventUpdateDto.getLocation() != null) {
            event.setLocation(LocationMapper.returnLocation(eventUpdateDto.getLocation()));
        }
        if (eventUpdateDto.getPaid() != null) {
            event.setPaid(eventUpdateDto.getPaid());
        }
        if (eventUpdateDto.getParticipantLimit() != null) {
            event.setParticipantLimit(eventUpdateDto.getParticipantLimit());
        }
        if (eventUpdateDto.getRequestModeration() != null) {
            event.setRequestModeration(eventUpdateDto.getRequestModeration());
        }
        if (eventUpdateDto.getStateAction() != null) {
            if (eventUpdateDto.getStateAction() == StateAction.PUBLISH_EVENT) {
                event.setState(PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if (eventUpdateDto.getStateAction() == StateAction.REJECT_EVENT ||
                    eventUpdateDto.getStateAction() == StateAction.CANCEL_REVIEW) {
                event.setState(State.CANCELED);
            } else if (eventUpdateDto.getStateAction() == StateAction.SEND_TO_REVIEW) {
                event.setState(State.PENDING);
            }
        }
        if (eventUpdateDto.getTitle() != null && !eventUpdateDto.getTitle().isBlank()) {
            event.setTitle(eventUpdateDto.getTitle());
        }

        locationRepository.save(event.getLocation());
        return eventRepository.save(event);
    }

    private void sendInfo(String uri, String ip) {
        HitDto hitDto = HitDto.builder()
                .app("ewm-service")
                .uri(uri)
                .ip(ip)
                .timestamp(LocalDateTime.now())
                .build();
        client.addHit(hitDto);
    }

    private Long getViewsEventById(Long eventId) {

        String uri = "/events/" + eventId;
        ResponseEntity<Object> response = client.findStats(START_HISTORY, LocalDateTime.now(), uri, true);
        List<StatsDto> result = objectMapper.convertValue(response.getBody(), new TypeReference<>() {
        });

        if (result.isEmpty()) {
            return 0L;
        } else {
            return result.get(0).getHits();
        }
    }

    public LocalDateTime parseDate(String date) {
        if (date != null) {
            return LocalDateTime.parse(date, FORMATTER);
        } else {
            return null;
        }
    }
}