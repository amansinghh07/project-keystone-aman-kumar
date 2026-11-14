package com.azentio.keystone;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OnboardingApiTest {
  @Test void panIsAlwaysMaskedWithOnlyFinalFiveCharactersVisible() { assertEquals("*****1234F",OnboardingService.maskPan("ABCDE1234F")); }
  @Test void mobileIsAlwaysMaskedWithOnlyFinalFourCharactersVisible() { assertEquals("******3210",OnboardingService.maskMobile("9876543210")); }
}
