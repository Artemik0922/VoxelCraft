package com.voxelgame.chat.commands;

/**
 * /weather <clear|rain|thunder|snow> — управление погодой.
 *
 * Currently sets a state flag that can be read by particle systems
 * and the renderer. Actual particle effects (rain/snow) can be
 * implemented on top.
 */
public class WeatherCommand implements Command {

    /** Interface to change the weather. */
    public interface WeatherChanger {
        String currentWeather();
        void setWeather(String weather);
    }

    public static WeatherChanger changer;

    @Override
    public String getName() { return "weather"; }

    @Override
    public String getUsage() { return "/weather <clear|rain|thunder|snow>"; }

    @Override
    public String getDescription() { return "управление погодой"; }

    @Override
    public String execute(String[] args) {
        if (args.length < 1) {
            if (changer != null) {
                return "Текущая погода: " + changer.currentWeather();
            }
            throw new IllegalArgumentException("укажи: clear, rain, thunder или snow");
        }

        if (changer == null) {
            return "Нет активного мира.";
        }

        String weather = args[0].toLowerCase();
        switch (weather) {
            case "clear":
            case "sun":
            case "sunny":
                changer.setWeather("clear");
                return "Погода: ясно";
            case "rain":
            case "rainy":
                changer.setWeather("rain");
                return "Погода: дождь";
            case "thunder":
            case "storm":
            case "thunderstorm":
                changer.setWeather("thunder");
                return "Погода: гроза";
            case "snow":
            case "snowy":
                changer.setWeather("snow");
                return "Погода: снег";
            default:
                throw new IllegalArgumentException("неизвестная погода '" + weather + "'");
        }
    }
}
