// 주인: BE-B
package com.ieum.profile;

/** 없는 항목과 다른 수료생의 항목은 같은 404로 처리한다. */
public class ProfileItemNotFoundException extends RuntimeException {

    public ProfileItemNotFoundException() {
        super("프로필 항목을 찾을 수 없어요.");
    }
}
