// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;

/** 기본정보는 수료생당 하나이며 항목 번호로 수료생 번호를 사용한다. */
final class ProfileBasicWriter {

    private final ProfileMapper mapper;

    ProfileBasicWriter(ProfileMapper mapper) {
        this.mapper = mapper;
    }

    // 호출 서비스가 먼저 수료생 잠금을 잡고 같은 트랜잭션에서 실행한다.
    void update(Long studentId, Long itemId, JsonNode item) {
        if (!studentId.equals(itemId)) throw new ProfileItemNotFoundException();
        ProfileItemValues.validatePatch("BASIC", item);
        ProfileData.Student stored = mapper.findCurrentStudent(studentId);
        if (stored == null) throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        mapper.updateBasic(ProfileItemValues.basic(item, stored));
    }
}
