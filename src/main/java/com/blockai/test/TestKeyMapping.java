package com.blockai.test;

import net.minecraft.client.KeyMapping;

public class TestKeyMapping {
    public static void main(String[] args) {
        for (java.lang.reflect.Constructor<?> c : KeyMapping.class.getConstructors()) {
            System.out.println(c);
        }
    }
}
