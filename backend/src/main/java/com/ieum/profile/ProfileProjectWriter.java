// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.databind.JsonNode;

/** 호출 서비스의 트랜잭션과 수료생 잠금 안에서만 사용한다. */
final class ProfileProjectWriter {

    private final ProfileMapper mapper;

    ProfileProjectWriter(ProfileMapper mapper) {
        this.mapper = mapper;
    }

    Long add(Long studentId, JsonNode item, boolean allowOriginals) {
        ProfileData.Project project = ProfileProjectValues.merge(item, null, allowOriginals);
        mapper.insertProject(studentId, project);
        if (project.getItemId() == null) throw new IllegalStateException("프로젝트 번호가 생성되지 않았어요.");
        return project.getItemId();
    }

    boolean update(Long studentId, Long itemId, JsonNode item, boolean allowOriginals) {
        ProfileProjectValues.validatePatch(item, allowOriginals);
        ProfileData.Project stored = requireProject(studentId, itemId);
        if (!allowOriginals && !ProfileProjectValues.hasMetadata(item)) return false;
        ProfileData.Project project = ProfileProjectValues.merge(item, stored, allowOriginals);
        if (allowOriginals) mapper.updateProjectOriginal(studentId, project);
        else mapper.updateProjectMetadata(studentId, project);
        return true;
    }

    boolean updateInclude(Long studentId, Long itemId, JsonNode item) {
        boolean include = ProfileProjectValues.include(item);
        requireProject(studentId, itemId);
        mapper.updateProjectInclude(studentId, itemId, include);
        return true;
    }

    boolean updatePortfolio(Long studentId, Long itemId, JsonNode item) {
        ProfileProjectValues.validatePortfolioPatch(item);
        if (!item.has("fileName")) return updateInclude(studentId, itemId, item);
        ProfileData.Project stored = requireProject(studentId, itemId);
        String fileName = ProfileProjectValues.portfolioFileName(item, stored.getPortfolioFileName());
        boolean include = item.has("include") ? ProfileProjectValues.include(item) : stored.isIncludeInApply();
        mapper.updatePortfolio(studentId, itemId, fileName, include);
        return true;
    }

    void deletePortfolio(Long studentId, Long itemId) {
        requireProject(studentId, itemId);
        // 첨부만 제거한다. 프로젝트와 AI 근거인 README·역할 기록은 유지한다.
        mapper.updatePortfolio(studentId, itemId, null, false);
    }

    void excludePortfolio(Long studentId, Long itemId) {
        requireProject(studentId, itemId);
        // 서류에서 제외해도 M8에 등록한 첨부 파일명과 프로젝트 원문은 유지한다.
        mapper.updateProjectInclude(studentId, itemId, false);
    }

    void delete(Long studentId, Long itemId) {
        requireProject(studentId, itemId);
        mapper.deleteProject(studentId, itemId);
    }

    private ProfileData.Project requireProject(Long studentId, Long itemId) {
        ProfileData.Project stored = mapper.findProjectItem(studentId, itemId);
        if (stored == null) throw new ProfileItemNotFoundException();
        return stored;
    }
}
