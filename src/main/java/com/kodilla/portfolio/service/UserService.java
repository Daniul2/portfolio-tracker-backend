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

    /** Database write #7: create a user. */
    @Transactional
    public UserResponse create(UserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username '" + request.username() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email '" + request.email() + "' is already registered");
        }

        User saved = userRepository.save(
                new User(request.username(), request.email(), request.displayName()));
        auditService.record("USER_CREATED", ENTITY, saved.getId(), "username=" + saved.getUsername());
        return DtoMapper.toUserResponse(saved, 0);
    }

    /** Database write #8: update a user. */
    @Transactional
    public UserResponse update(Long id, UserRequest request) {
        User user = requireUser(id);

        if (!user.getUsername().equals(request.username())
                && userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username '" + request.username() + "' is already taken");
        }
        if (!user.getEmail().equals(request.email())
                && userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email '" + request.email() + "' is already registered");
        }

        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setDisplayName(request.displayName());

        User saved = userRepository.save(user);
        auditService.record("USER_UPDATED", ENTITY, id, "username=" + saved.getUsername());
        return DtoMapper.toUserResponse(saved, portfolioRepository.countByUserId(id));
    }

    /** Database write #9: delete a user and, by cascade, their portfolios. */
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
}
