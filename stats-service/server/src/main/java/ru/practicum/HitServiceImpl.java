package ru.practicum;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.HitDto;
import ru.practicum.dto.StatsDto;
import ru.practicum.exception.StatsValidationException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HitServiceImpl implements HitService {

    private final HitRepository hitRepository;

    @Transactional
    @Override
    public void addHit(HitDto hitDto) {

        hitRepository.save(HitMapper.returnHit(hitDto));
    }

    @Transactional(readOnly = true)
    @Override
    public List<StatsDto> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {

        if (start != null && end != null) {
            if (start.isAfter(end)) {
                throw new StatsValidationException("Ошибка даты.");
            }
        }

        if (uris == null || uris.isEmpty()) {
            if (unique) {
                return hitRepository.findAllStatsByDistinctUniqueIp(start, end);
            } else {
                return hitRepository.findAllStatsWithoutDistinctIp(start, end);
            }
        } else {
            if (unique) {
                return hitRepository.findStatsByUrisByDistinctUniqueIp(start, end, uris);
            } else {
                return hitRepository.findStatsByUrisWithoutDistinctIp(start, end, uris);
            }
        }
    }
}
