package com.kodilla.portfolio.service;

import com.kodilla.portfolio.domain.User;
import com.kodilla.portfolio.dto.UserDtos.UserRequest;
import com.kodilla.portfolio.dto.UserDtos.UserResponse;
import com.kodilla.portfolio.exception.DuplicateResourceException;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.mapper.DtoMapper;
import com.kodilla.portfolio.repository.PortfolioRepository;
import com.kodilla.portfolio.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private static final String ENTITY = "User";

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       PortfolioRepository portfolioRepository,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(user -> DtoMapper.toUserResponse(user, portfolioRepository.countByUserId(user.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        User user = requireUser(id);
        return DtoMapper.toUserResponse(user, portfolioRepository.countByUserId(id));
    }

    @Transactional(readOnly = true)
    public UserResponse findByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("No user with username " + username));
        return DtoMapper.toUserResponse(user, portfolioRepository.countByUserId(user.getId()));
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        requireUsernameFree(request.username());
        requireEmailFree(request.email());

        User saved = userRepository.save(
                new User(request.username(), request.email(), request.displayName()));
        auditService.record("USER_CREATED", ENTITY, saved.getId(), "username=" + saved.getUsername());
        return DtoMapper.toUserResponse(saved, 0);
    }

    /** Uniqueness is only checked for values that actually change. */
    @Transactional
    public UserResponse update(Long id, UserRequest request) {
        User user = requireUser(id);
        if (!user.getUsername().equals(request.username())) {
            requireUsernameFree(request.username());
        }
        if (!user.getEmail().equals(request.email())) {
            requireEmailFree(request.email());
        }

        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setDisplayName(request.displayName());

        User saved = userRepository.save(user);
        auditService.record("USER_UPDATED", ENTITY, id, "username=" + saved.getUsername());
        return DtoMapper.toUserResponse(saved, portfolioRepository.countByUserId(id));
    }

    /** Deletes the user and, by cascade, everything they own. */
    @Transactional
    public void delete(Long id) {
        User user = requireUser(id);
        String username = user.getUsername();
        userRepository.delete(user);
        auditService.record("USER_DELETED", ENTITY, id, "username=" + username);
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private void requireUsernameFree(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username '" + username + "' is already taken");
        }
    }

    private void requireEmailFree(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }
    }
}
