// 주인: BE-A
package com.ieum.application;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * PATCH /api/applications/{applicationId}/document 요청 (M6 · M6-1 · M6-2). docs/API.md 2-1.
 * op: UPDATE_SENTENCE(sentenceId, text) · ADD_ITEM(section, item) · UPDATE_ITEM(section, itemId, item) · DELETE_ITEM(section, itemId)
 */
public record DocumentPatchRequest(
        String op,
        String sentenceId,
        String text,
        String section,
        String itemId,
        JsonNode item,
        Boolean saveToProfile
) {

    boolean save() {
        return Boolean.TRUE.equals(saveToProfile);
    }
}
