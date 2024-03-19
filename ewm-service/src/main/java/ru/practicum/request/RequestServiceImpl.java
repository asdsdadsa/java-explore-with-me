package ru.practicum.request;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.event.EventRepository;
import ru.practicum.event.model.Event;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.request.dto.RequestDto;
import ru.practicum.user.User;
import ru.practicum.user.UserRepository;
import ru.practicum.util.enums.State;
import ru.practicum.util.enums.Status;

import java.time.LocalDateTime;
import java.util.List;

import static ru.practicum.request.RequestMapper.returnRequestDto;
import static ru.practicum.request.RequestMapper.returnRequestDtoList;

@Slf4j
@Service
@AllArgsConstructor
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RequestDto addRequest(Long userId, Long eventId) {

        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Пользователя с id " + userId + " не существует"));
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("События с id " + eventId + " не существует"));

        if (event.getParticipantLimit() <= event.getConfirmedRequests() && event.getParticipantLimit() != 0) {
            throw new ConflictException("Превышен лимит.");
        }

        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("По такому user id нельзя отправить request на участие в event.");
        }

        if (requestRepository.findByRequesterIdAndEventId(userId, eventId).isPresent()) {
            throw new ConflictException("Заявка уже была подана.");
        }

        if (event.getState() != State.PUBLISHED) {
            throw new ConflictException("Событие не опубликовано,нельзя запросить участие.");
        } else {

            Request request = Request.builder()
                    .requester(user)
                    .event(event)
                    .created(LocalDateTime.now())
                    .status(Status.PENDING)
                    .build();

            if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
                request.setStatus(Status.CONFIRMED);
                request = requestRepository.save(request);
                event.setConfirmedRequests(requestRepository.countAllByEventIdAndStatus(eventId, Status.CONFIRMED));
                eventRepository.save(event);

                return returnRequestDto(request);
            }

            request = requestRepository.save(request);

            return returnRequestDto(request);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<RequestDto> getRequestsByUserId(Long userId) {
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Пользователя с id " + userId + " не существует"));

        List<Request> requestList = requestRepository.findByRequesterId(userId);

        return returnRequestDtoList(requestList);
    }

    @Override
    @Transactional
    public RequestDto cancelRequest(Long userId, Long requestId) {
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Пользователя с id " + userId + " не существует"));

        Request request = requestRepository.findById(requestId).orElseThrow(() -> new NotFoundException("Запроса с id " + userId + " не существует"));
        request.setStatus(Status.CANCELED);

        return returnRequestDto(requestRepository.save(request));
    }
}
