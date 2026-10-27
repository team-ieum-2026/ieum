// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.databind.JsonNode;

/** 병역은 수료생당 하나이며 호출 서비스의 잠금과 트랜잭션 안에서 수정한다. */
final class ProfileMilitaryWriter {

    private final ProfileMapper mapper;

    ProfileMilitaryWriter(ProfileMapper mapper) {
        this.mapper = mapper;
    }

    void update(Long studentId, Long itemId, JsonNode item) {
        if (!studentId.equals(itemId)) throw new ProfileItemNotFoundException();
        mapper.updateMilitary(studentId, ProfileItemValues.military(item));
    }
}
