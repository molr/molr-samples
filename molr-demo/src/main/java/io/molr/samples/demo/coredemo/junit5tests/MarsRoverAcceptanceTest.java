package io.molr.samples.demo.coredemo.junit5tests;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MarsRoverAcceptanceTest {

    @Test
    public void parachuteDeploysCorrectly() {
        System.out.println("Parachute deployed");
        Assertions.assertTrue(true);
    }

    @Test
    public void landingGearDeploys() {
        Assertions.assertEquals("deployed", "stuck");
    }


}
