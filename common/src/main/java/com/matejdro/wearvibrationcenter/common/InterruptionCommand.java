package com.matejdro.wearvibrationcenter.common;

public interface InterruptionCommand
{
    boolean shouldNotVibrateInTheater();
    boolean shouldNotVibrateOnCharger();

}
