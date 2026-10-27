// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;

/** 학력·경력·자격증 원문을 호출 서비스의 트랜잭션과 수료생 잠금 안에서 저장한다. */
final class ProfileRecordWriter {

    private final ProfileMapper mapper;

    ProfileRecordWriter(ProfileMapper mapper) {
        this.mapper = mapper;
    }

    static boolean supports(String section) {
        return "EDUCATION".equals(section) || "CAREER".equals(section) || "CERTIFICATES".equals(section);
    }

    Long add(Long studentId, String section, JsonNode item) {
        Long itemId;
        if ("EDUCATION".equals(section)) {
            var education = educationData(studentId, null, ProfileItemValues.education(item, null));
            mapper.insertEducation(education);
            itemId = education.getItemId();
        } else if ("CAREER".equals(section)) {
            var career = careerData(studentId, null, ProfileItemValues.career(item, null));
            mapper.insertCareer(career);
            itemId = career.getItemId();
        } else if ("CERTIFICATES".equals(section)) {
            var certificate = certificateData(studentId, null, ProfileItemValues.certificate(item, null));
            mapper.insertCertificate(certificate);
            itemId = certificate.getItemId();
        } else {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (itemId == null) throw new IllegalStateException("프로필 항목 번호가 생성되지 않았어요.");
        return itemId;
    }

    void update(Long studentId, String section, Long itemId, JsonNode item) {
        ProfileItemValues.validatePatch(section, item);
        if ("EDUCATION".equals(section)) {
            var stored = requireEducation(studentId, itemId);
            mapper.updateEducation(educationData(studentId, itemId, ProfileItemValues.education(item, stored)));
        } else if ("CAREER".equals(section)) {
            var stored = requireCareer(studentId, itemId);
            mapper.updateCareer(careerData(studentId, itemId, ProfileItemValues.career(item, stored)));
        } else if ("CERTIFICATES".equals(section)) {
            var stored = requireCertificate(studentId, itemId);
            mapper.updateCertificate(certificateData(studentId, itemId, ProfileItemValues.certificate(item, stored)));
        } else {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    void delete(Long studentId, String section, Long itemId) {
        if ("EDUCATION".equals(section)) {
            requireEducation(studentId, itemId);
            mapper.deleteEducation(studentId, itemId);
        } else if ("CAREER".equals(section)) {
            requireCareer(studentId, itemId);
            mapper.deleteCareer(studentId, itemId);
        } else if ("CERTIFICATES".equals(section)) {
            requireCertificate(studentId, itemId);
            mapper.deleteCertificate(studentId, itemId);
        } else {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private StudentProfileResponse.Education requireEducation(Long studentId, Long itemId) {
        var stored = mapper.findEducationItem(studentId, itemId);
        if (stored == null) throw new ProfileItemNotFoundException();
        return stored;
    }

    private StudentProfileResponse.Career requireCareer(Long studentId, Long itemId) {
        var stored = mapper.findCareerItem(studentId, itemId);
        if (stored == null) throw new ProfileItemNotFoundException();
        return stored;
    }

    private StudentProfileResponse.Certificate requireCertificate(Long studentId, Long itemId) {
        var stored = mapper.findCertificate(studentId, itemId);
        if (stored == null) throw new ProfileItemNotFoundException();
        return stored;
    }

    private ProfileData.Education educationData(
            Long studentId, Long itemId, ProfileItemRequest.EducationItem item) {
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

    private ProfileData.Certificate certificateData(
            Long studentId, Long itemId, ProfileItemRequest.CertificateItem item) {
        var certificate = new ProfileData.Certificate();
        certificate.setStudentId(studentId);
        certificate.setItemId(itemId);
        certificate.setName(item.name());
        certificate.setIssuer(item.issuer());
        certificate.setAcquiredOn(item.acquiredOn());
        return certificate;
    }
}
