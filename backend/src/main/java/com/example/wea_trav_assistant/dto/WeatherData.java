package com.example.wea_trav_assistant.dto;

import java.time.LocalDate;

public record WeatherData(String city, String adminArea, String country,
                          Double latitude, Double longitude, LocalDate date,
                          Integer weatherCode, Double maxTemp, Double minTemp,
                          Integer precipitationProbability, Double precipitation,
                          Double windSpeed, String source) { }
