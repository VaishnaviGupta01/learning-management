package com.lms.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.dto.course.CourseDetailResponse;
import com.lms.dto.course.CourseRequest;
import com.lms.dto.course.CourseResponse;
import com.lms.dto.course.ModuleRequest;
import com.lms.dto.course.ModuleResponse;
import com.lms.dto.course.TopicResponse;
import com.lms.entity.Course;
import com.lms.entity.CourseModule;
import com.lms.entity.User;
import com.lms.exception.ConflictException;
import com.lms.exception.ResourceNotFoundException;
import com.lms.repository.CourseModuleRepository;
import com.lms.repository.CourseRepository;
import com.lms.repository.TopicPrerequisiteRepository;
import com.lms.repository.UserRepository;
import com.lms.security.UserPrincipal;

@Service
@Transactional
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final TopicPrerequisiteRepository prerequisiteRepository;
    private final UserRepository userRepository;

    public CourseService(CourseRepository courseRepository, CourseModuleRepository moduleRepository,
                         TopicPrerequisiteRepository prerequisiteRepository, UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.userRepository = userRepository;
    }

    // ------------------------------------------------------------------ courses

    /** Admins see all courses, instructors their own plus published ones, students only published ones. */
    @Transactional(readOnly = true)
    public List<CourseResponse> listCourses(UserPrincipal user) {
        List<Course> courses;
        if (user.isAdmin()) {
            courses = courseRepository.findAll();
        } else if (user.isInstructor()) {
            courses = courseRepository.findByInstructorIdOrPublishedTrue(user.getId());
        } else {
            courses = courseRepository.findByPublishedTrue();
        }
        return courses.stream().map(CourseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CourseDetailResponse getCourse(Long courseId, UserPrincipal user) {
        Course course = findCourse(courseId);
        CourseAccess.requireView(course, user);
        Map<Long, List<Long>> prereqs = prerequisiteIdsByTopic(courseId);
        List<ModuleResponse> modules = course.getModules().stream()
                .map(m -> toModuleResponse(m, prereqs))
                .toList();
        return new CourseDetailResponse(course.getId(), course.getCode(), course.getTitle(), course.getDescription(),
                course.isPublished(), course.getInstructor().getId(),
                course.getInstructor().getFirstName() + " " + course.getInstructor().getLastName(), modules);
    }

    public CourseResponse createCourse(CourseRequest request, UserPrincipal user) {
        String code = request.code().trim().toUpperCase();
        if (courseRepository.existsByCode(code)) {
            throw new ConflictException("Course code already in use: " + code);
        }
        User instructor = userRepository.getReferenceById(user.getId());
        Course course = new Course();
        course.setCode(code);
        course.setInstructor(instructor);
        applyCourseFields(course, request);
        return CourseResponse.from(courseRepository.save(course));
    }

    public CourseResponse updateCourse(Long courseId, CourseRequest request, UserPrincipal user) {
        Course course = findCourse(courseId);
        CourseAccess.requireManage(course, user);
        String code = request.code().trim().toUpperCase();
        if (!code.equals(course.getCode()) && courseRepository.existsByCode(code)) {
            throw new ConflictException("Course code already in use: " + code);
        }
        course.setCode(code);
        applyCourseFields(course, request);
        return CourseResponse.from(course);
    }

    public CourseResponse setPublished(Long courseId, boolean published, UserPrincipal user) {
        Course course = findCourse(courseId);
        CourseAccess.requireManage(course, user);
        course.setPublished(published);
        return CourseResponse.from(course);
    }

    public void deleteCourse(Long courseId, UserPrincipal user) {
        Course course = findCourse(courseId);
        CourseAccess.requireManage(course, user);
        prerequisiteRepository.deleteAll(prerequisiteRepository.findByTopicModuleCourseId(courseId));
        courseRepository.delete(course);
        courseRepository.flush(); // surface FK violations (quizzes, progress, ...) as 409 here
    }

    // ------------------------------------------------------------------ modules

    @Transactional(readOnly = true)
    public List<ModuleResponse> listModules(Long courseId, UserPrincipal user) {
        Course course = findCourse(courseId);
        CourseAccess.requireView(course, user);
        Map<Long, List<Long>> prereqs = prerequisiteIdsByTopic(courseId);
        return course.getModules().stream().map(m -> toModuleResponse(m, prereqs)).toList();
    }

    public ModuleResponse createModule(Long courseId, ModuleRequest request, UserPrincipal user) {
        Course course = findCourse(courseId);
        CourseAccess.requireManage(course, user);
        CourseModule module = new CourseModule();
        module.setCourse(course);
        module.setTitle(request.title().trim());
        module.setDescription(request.description());
        module.setOrderIndex(request.orderIndex() != null ? request.orderIndex() : course.getModules().size());
        course.getModules().add(module);
        moduleRepository.save(module);
        return toModuleResponse(module, Map.of());
    }

    public ModuleResponse updateModule(Long moduleId, ModuleRequest request, UserPrincipal user) {
        CourseModule module = findModule(moduleId);
        CourseAccess.requireManage(module.getCourse(), user);
        module.setTitle(request.title().trim());
        module.setDescription(request.description());
        if (request.orderIndex() != null) {
            module.setOrderIndex(request.orderIndex());
        }
        return toModuleResponse(module, prerequisiteIdsByTopic(module.getCourse().getId()));
    }

    public void deleteModule(Long moduleId, UserPrincipal user) {
        CourseModule module = findModule(moduleId);
        Course course = module.getCourse();
        CourseAccess.requireManage(course, user);
        module.getTopics().forEach(t -> prerequisiteRepository.deleteByTopicIdOrPrerequisiteTopicId(t.getId(), t.getId()));
        course.getModules().remove(module); // orphanRemoval deletes the module and its topics
        moduleRepository.flush();
    }

    // ------------------------------------------------------------------ helpers

    Course findCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
    }

    private CourseModule findModule(Long moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module", moduleId));
    }

    private static void applyCourseFields(Course course, CourseRequest request) {
        course.setTitle(request.title().trim());
        course.setDescription(request.description());
        if (request.published() != null) {
            course.setPublished(request.published());
        }
    }

    private Map<Long, List<Long>> prerequisiteIdsByTopic(Long courseId) {
        return prerequisiteRepository.findByTopicModuleCourseId(courseId).stream()
                .collect(Collectors.groupingBy(p -> p.getTopic().getId(),
                        Collectors.mapping(p -> p.getPrerequisiteTopic().getId(), Collectors.toList())));
    }

    private static ModuleResponse toModuleResponse(CourseModule m, Map<Long, List<Long>> prereqs) {
        List<TopicResponse> topics = m.getTopics().stream()
                .map(t -> TopicResponse.from(t, prereqs.getOrDefault(t.getId(), List.of())))
                .toList();
        return new ModuleResponse(m.getId(), m.getCourse().getId(), m.getTitle(), m.getDescription(),
                m.getOrderIndex(), topics);
    }
}
