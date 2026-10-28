// 주인: BE-B
package com.ieum.helprequest;

public record HelpRequestUpdateResult(Long helpRequestId, String status, String contactMethod, String memo) {}
