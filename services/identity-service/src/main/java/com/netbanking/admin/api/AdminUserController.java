package com.netbanking.admin.api;

import com.netbanking.admin.service.AdminUserService;
import com.netbanking.common.api.PagedResponse;
import com.netbanking.security.SecurityContextHelper;
import com.netbanking.user.domain.UserStatus;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminUserService users;

    public AdminUserController(AdminUserService users) {
        this.users = users;
    }

    @GetMapping
    public PagedResponse<AdminUserResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(users.search(query, status, page, size));
    }

    @PatchMapping("/{userId}/status")
    public AdminUserResponse changeStatus(
            @PathVariable Long userId, @Valid @RequestBody AdminUserStatusRequest request) {
        return users.changeStatus(
                SecurityContextHelper.currentUserId(), userId, request.status());
    }
}
