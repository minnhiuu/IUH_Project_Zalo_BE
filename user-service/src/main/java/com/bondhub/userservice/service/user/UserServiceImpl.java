package com.bondhub.userservice.service.user;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.userservice.dto.request.UserCreateRequest;
import com.bondhub.userservice.dto.request.UserUpdateRequest;
import com.bondhub.userservice.dto.response.UserResponse;
import com.bondhub.userservice.mapper.UserMapper;
import com.bondhub.userservice.model.User;
import com.bondhub.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public UserResponse createUser(UserCreateRequest request) {
        log.info("Creating user with accountId: {}", request.getAccountId());
        User user = userMapper.toUser(request);
        user = userRepository.save(user);
        log.info("User created successfully with id: {}", user.getId());
        return userMapper.toUserResponse(user);
    }

    @Override
    public UserResponse getUserById(String id) {
        log.info("Fetching user with id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toUserResponse(user);
    }

    @Override
    public List<UserResponse> getAllUsers() {
        log.info("Fetching all users");
        return userRepository.findAll().stream()
                .map(userMapper::toUserResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponse updateUser(String id, UserUpdateRequest request) {
        log.info("Updating user with id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        userMapper.updateUserFromRequest(user, request);
        user = userRepository.save(user);

        log.info("User updated successfully with id: {}", id);
        return userMapper.toUserResponse(user);
    }

    @Override
    public void deleteUser(String id) {
        log.info("Deleting user with id: {}", id);
        if (!userRepository.existsById(id)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        userRepository.deleteById(id);
        log.info("User deleted successfully with id: {}", id);
    }
}
