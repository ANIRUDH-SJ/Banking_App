package com.netbanking.branch.api;

public record BranchResponse(
        Long branchId,
        Long bankId,
        String branchCode,
        String branchName,
        String ifscCode,
        String city,
        String state) {}
