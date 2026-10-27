// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.ai.AiClient;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 프로필 자료 변경과 AI 재추출 결과 저장을 하나의 트랜잭션으로 처리한다. */
@Service
public class ProfileItemService {

    private final ProfileMapper mapper;
    private final ProfileService profileService;
    private final ProfileBasicWriter basic;
    private final ProfileMilitaryWriter military;
    private final ProfileSkillsWriter skills;
    private final ProfileProjectWriter projects;
    private final ProfileExtraction extraction;

    public ProfileItemService(
            ProfileMapper mapper, ProfileService profileService, AiClient aiClient, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.profileService = profileService;
        this.basic = new ProfileBasicWriter(mapper);
        this.military = new ProfileMilitaryWriter(mapper);
        this.skills = new ProfileSkillsWriter(mapper);
        this.projects = new ProfileProjectWriter(mapper);
        this.extraction = new ProfileExtraction(mapper, aiClient, objectMapper);
    }

    @Transactional
    public StudentProfileResponse addItem(Long studentId, ProfileItemRequest request) {
        if (mapper.lockStudent(studentId) == null) {
            throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        }

        if ("CERTIFICATES".equals(request.section())) {
            var item = request.certificate();
            var certificate = new ProfileData.Certificate();
            certificate.setStudentId(studentId);
            certificate.setName(item.name());
            certificate.setIssuer(item.issuer());
            certificate.setAcquiredOn(item.acquiredOn());
            mapper.insertCertificate(certificate);
        } else if ("EDUCATION".equals(request.section())) {
            mapper.insertEducation(educationData(studentId, null, request.education()));
        } else if ("CAREER".equals(request.section())) {
            mapper.insertCareer(careerData(studentId, null, request.career()));
        } else if ("PROJECTS".equals(request.section())) {
            projects.add(studentId, request.item(), true);
        } else {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        return extractAndReturn(studentId);
    }

    @Transactional
    public StudentProfileResponse updateItem(Long studentId, Long itemId, ProfileItemPatchRequest request) {
        if (mapper.lockStudent(studentId) == null) {
            throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        }
        if ("BASIC".equals(request.section())) {
            basic.update(studentId, itemId, request.item());
        } else if ("MILITARY".equals(request.section())) {
            military.update(studentId, itemId, request.item());
        } else if ("SKILLS".equals(request.section())) {
            skills.update(studentId, itemId, request.item(), false);
        } else if ("CERTIFICATES".equals(request.section())) {
            StudentProfileResponse.Certificate stored = requireCertificate(studentId, itemId);
            var item = request.applyTo(stored);
            var certificate = new ProfileData.Certificate();
            certificate.setStudentId(studentId);
            certificate.setItemId(itemId);
            certificate.setName(item.name());
            certificate.setIssuer(item.issuer());
            certificate.setAcquiredOn(item.acquiredOn());
            mapper.updateCertificate(certificate);
        } else if ("EDUCATION".equals(request.section())) {
            StudentProfileResponse.Education stored = requireEducation(studentId, itemId);
            mapper.updateEducation(educationData(studentId, itemId, request.applyTo(stored)));
        } else if ("CAREER".equals(request.section())) {
            StudentProfileResponse.Career stored = requireCareer(studentId, itemId);
            mapper.updateCareer(careerData(studentId, itemId, request.applyTo(stored)));
        } else if ("PROJECTS".equals(request.section())) {
            projects.update(studentId, itemId, request.item(), true);
        } else if ("PORTFOLIO".equals(request.section())) {
            projects.updatePortfolio(studentId, itemId, request.item());
        } else {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return extractAndReturn(studentId);
    }

    @Transactional
    public StudentProfileResponse deleteItem(Long studentId, Long itemId, String section) {
        if (!"CERTIFICATES".equals(section) && !"EDUCATION".equals(section)
                && !"CAREER".equals(section) && !"PROJECTS".equals(section) && !"PORTFOLIO".equals(section)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (mapper.lockStudent(studentId) == null) {
            throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        }
        if ("CERTIFICATES".equals(section)) {
            requireCertificate(studentId, itemId);
            mapper.deleteCertificate(studentId, itemId);
        } else if ("EDUCATION".equals(section)) {
            requireEducation(studentId, itemId);
            mapper.deleteEducation(studentId, itemId);
        } else if ("CAREER".equals(section)) {
            requireCareer(studentId, itemId);
            mapper.deleteCareer(studentId, itemId);
        } else if ("PROJECTS".equals(section)) {
            projects.delete(studentId, itemId);
        } else {
            projects.deletePortfolio(studentId, itemId);
        }
        return extractAndReturn(studentId);
    }

    @Transactional
    public StudentProfileResponse updateCoverLetter(Long studentId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (mapper.lockStudent(studentId) == null) {
            throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        }
        mapper.upsertCoverLetter(studentId, content);
        return extractAndReturn(studentId);
    }

    private ProfileData.Career careerData(Long studentId, Long itemId, ProfileItemRequest.CareerItem item) {
        var career = new ProfileData.Career();
        career.setStudentId(studentId);
        career.setItemId(itemId);
        career.setCompanyName(item.companyName());
        career.setPosition(item.position());
        career.setStartedOn(item.startedOn());
        career.setEndedOn(item.endedOn());
        career.setDescription(item.description());
        return career;
    }

    private StudentProfileResponse.Career requireCareer(Long studentId, Long itemId) {
        StudentProfileResponse.Career career = mapper.findCareerItem(studentId, itemId);
        if (career == null) throw new ProfileItemNotFoundException();
        return career;
    }

    private ProfileData.Education educationData(Long studentId, Long itemId, ProfileItemRequest.EducationItem item) {
        var education = new ProfileData.Education();
        education.setStudentId(studentId);
        education.setItemId(itemId);
        education.setSchoolName(item.schoolName());
        education.setMajor(item.major());
        education.setDegree(item.degree());
        education.setStatus(item.status());
        education.setGraduatedOn(item.graduatedOn());
        return education;
    }

    private StudentProfileResponse.Education requireEducation(Long studentId, Long itemId) {
        StudentProfileResponse.Education education = mapper.findEducationItem(studentId, itemId);
        if (education == null) {
            throw new ProfileItemNotFoundException();
        }
        return education;
    }

    private StudentProfileResponse.Certificate requireCertificate(Long studentId, Long itemId) {
        StudentProfileResponse.Certificate certificate = mapper.findCertificate(studentId, itemId);
        if (certificate == null) {
            throw new ProfileItemNotFoundException();
        }
        return certificate;
    }

    private StudentProfileResponse extractAndReturn(Long studentId) {
        extraction.refresh(studentId, profileService.getProfile(studentId));
        return profileService.getProfile(studentId);
    }
}
