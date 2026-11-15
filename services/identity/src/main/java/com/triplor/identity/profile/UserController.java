package com.triplor.identity.profile;

import java.util.List;
import java.util.UUID;

import com.triplor.common.security.CurrentUser;
import com.triplor.identity.profile.ProfileDtos.MyProfile;
import com.triplor.identity.profile.ProfileDtos.PublicProfile;
import com.triplor.identity.profile.ProfileDtos.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Profiles. Every endpoint here requires an access token. */
@RestController
@RequestMapping("/users")
public class UserController {

    private final ProfileService profileService;

    public UserController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    MyProfile me(CurrentUser user) {
        return profileService.getOwn(user.id());
    }

    @PatchMapping("/me")
    MyProfile updateMe(CurrentUser user, @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateOwn(user.id(), request);
    }

    @GetMapping("/{id}")
    PublicProfile byId(@PathVariable UUID id) {
        return profileService.getPublic(id);
    }

    /** Batch lookup: {@code GET /users?ids=a,b,c}. Returns only the users that exist. */
    @GetMapping
    List<PublicProfile> byIds(@RequestParam List<UUID> ids) {
        return profileService.getPublic(ids);
    }
}
