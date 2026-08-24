package com.skillatlas.people.dto;

import java.time.Instant;

/**
 * The freshly signed link plus the moment it stops working, so the client can refetch the profile
 * on time instead of guessing why an image started returning 403.
 */
public record AvatarResponse(String avatarUrl, Instant expiresAt) {
}
