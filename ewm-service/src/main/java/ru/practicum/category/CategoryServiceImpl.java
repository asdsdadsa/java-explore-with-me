package ru.practicum.category;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.category.dto.CategoryDto;
import ru.practicum.event.EventRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;

import java.util.List;

import static ru.practicum.category.CategoryMapper.returnCategoryDto;
import static ru.practicum.category.CategoryMapper.returnCategoryDtoList;

@Service
@AllArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;

    @Transactional
    @Override
    public CategoryDto addCategory(CategoryDto categoryDto) {

        Category category = CategoryMapper.returnCategory(categoryDto);
        categoryRepository.save(category);

        return returnCategoryDto(category);
    }

    @Transactional
    @Override
    public CategoryDto updateCategory(CategoryDto categoryDto, Long categoryId) {

        Category category = categoryRepository.findById(categoryId).orElseThrow(()
                -> new NotFoundException("Категории с id " + categoryId + " не существует"));
        category.setName(categoryDto.getName());
        categoryRepository.save(category);

        return returnCategoryDto(category);
    }

    @Transactional
    @Override
    public void deleteCategory(Long categoryId) {

        categoryRepository.findById(categoryId).orElseThrow(()
                -> new NotFoundException("Категории с id " + categoryId + " не существует"));

        if (!eventRepository.findByCategoryId(categoryId).isEmpty()) {
            throw new ConflictException("Этот идентификатор категории + " + categoryId + " используется и не может быть удален.");
        }

        categoryRepository.deleteById(categoryId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<CategoryDto> getCategories(Integer from, Integer size) {

        PageRequest pageRequest = PageRequest.of(from / size, size);

        return returnCategoryDtoList(categoryRepository.findAll(pageRequest));
    }

    @Transactional(readOnly = true)
    @Override
    public CategoryDto getCategoryById(Long categoryId) {

        return returnCategoryDto(categoryRepository.findById(categoryId).orElseThrow(()
                -> new NotFoundException("Категории с id " + categoryId + " не существует")));
    }
}