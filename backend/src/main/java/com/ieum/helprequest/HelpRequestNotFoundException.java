// 주인: BE-B
package com.ieum.helprequest;

public class HelpRequestNotFoundException extends RuntimeException {

    public HelpRequestNotFoundException() {
        super("도움 요청을 찾을 수 없어요.");
    }
}
