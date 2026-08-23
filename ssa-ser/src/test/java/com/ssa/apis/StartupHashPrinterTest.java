package com.ssa.apis;


import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class StartupHashPrinterTest {


    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println(encoder.encode("12345678"));
    }
}
