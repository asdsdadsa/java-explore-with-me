package ru.practicum.comments;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.comments.dto.CommentFullDto;
import ru.practicum.comments.dto.CommentNewDto;
import ru.practicum.comments.dto.CommentShortDto;
import ru.practicum.event.EventRepository;
import ru.practicum.event.model.Event;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.exception.ValidationException;
import ru.practicum.user.User;
import ru.practicum.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

import static ru.practicum.Util.CURRENT_TIME;
import static ru.practicum.Util.FORMATTER;
import static ru.practicum.comments.CommentMapper.*;

@Service
@AllArgsConstructor
public class CommentServiceImpl implements CommentService {
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public CommentFullDto addComment(Long userId, Long eventId, CommentNewDto commentNewDto) {

        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Пользователя с id " + userId + " не существует."));
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("События с id " + eventId + " не существует."));

        Comment comment = returnComment(commentNewDto, user, event);
        comment = commentRepository.save(comment);

        return returnCommentFullDto(comment);
    }

    @Override
    @Transactional
    public CommentFullDto updateComment(Long userId, Long commentId, CommentNewDto commentNewDto) {

        Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new NotFoundException("Комментария с id " + commentId + " не существует."));

        if (!userId.equals(comment.getUser().getId())) {
            throw new ConflictException("Пользователь с id " + userId + " не является автором комментария с id " + commentId + ".");
        }

        if (commentNewDto.getMessage() != null && !commentNewDto.getMessage().isBlank()) {
            comment.setMessage(commentNewDto.getMessage());
        }

        comment = commentRepository.save(comment);

        return returnCommentFullDto(comment);
    }

    @Override
    @Transactional
    public void deletePrivateComment(Long userId, Long commentId) {

        Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new NotFoundException("Комментария с id " + commentId + " не существует."));
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Пользователя с id " + userId + " не существует."));

        if (!comment.getUser().getId().equals(userId)) {
            throw new ConflictException("Пользователь с id " + userId + " не является автором комментария с id " + commentId + ".");
        }

        commentRepository.deleteById(commentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentShortDto> getCommentsByUserId(String rangeStart, String rangeEnd, Long userId, Integer from, Integer size) {

        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Пользователя с id " + userId + " не существует."));
        PageRequest pageRequest = PageRequest.of(from / size, size);

        LocalDateTime startTime = parseDate(rangeStart);
        LocalDateTime endTime = parseDate(rangeEnd);

        if (startTime != null && endTime != null) {
            if (startTime.isAfter(endTime)) {
                throw new ValidationException("Start должно быть после End.");
            }
            if (endTime.isAfter(CURRENT_TIME) || startTime.isAfter(CURRENT_TIME)) {
                throw new ValidationException("Дата должна быть в прошлом.");
            }
        }

        List<Comment> commentList = commentRepository.getCommentsByUserId(userId, startTime, endTime, pageRequest);

        return returnCommentShortDtoList(commentList);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentFullDto> getComments(String rangeStart, String rangeEnd, Integer from, Integer size) {

        PageRequest pageRequest = PageRequest.of(from / size, size);

        LocalDateTime startTime = parseDate(rangeStart);
        LocalDateTime endTime = parseDate(rangeEnd);

        if (startTime != null && endTime != null) {
            if (startTime.isAfter(endTime)) {
                throw new ValidationException("Start должно быть после End.");
            }
            if (endTime.isAfter(CURRENT_TIME) || startTime.isAfter(CURRENT_TIME)) {
                throw new ValidationException("Дата должна быть в прошлом.");
            }
        }

        List<Comment> commentList = commentRepository.getComments(startTime, endTime, pageRequest);

        return returnCommentFullDtoList(commentList);
    }

    @Override
    @Transactional
    public void deleteAdminComment(Long commentId) {

        commentRepository.findById(commentId).orElseThrow(() -> new NotFoundException("Комментария с id " + commentId + " не существует."));
        commentRepository.deleteById(commentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentShortDto> getCommentsByEventId(String rangeStart, String rangeEnd, Long eventId, Integer from, Integer size) {

        eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("События с id " + eventId + " не существует."));
        PageRequest pageRequest = PageRequest.of(from / size, size);

        LocalDateTime startTime = parseDate(rangeStart);
        LocalDateTime endTime = parseDate(rangeEnd);

        if (startTime != null && endTime != null) {
            if (startTime.isAfter(endTime)) {
                throw new ValidationException("Start должно быть после End.");
            }
            if (endTime.isAfter(CURRENT_TIME) || startTime.isAfter(CURRENT_TIME)) {
                throw new ValidationException("Дата должна быть в прошлом.");
            }
        }

        List<Comment> commentList = commentRepository.getCommentsByEventId(eventId, startTime, endTime, pageRequest);

        return returnCommentShortDtoList(commentList);
    }

    public LocalDateTime parseDate(String date) {
        if (date != null) {
            return LocalDateTime.parse(date, FORMATTER);
        } else {
            return null;
        }
    }
}