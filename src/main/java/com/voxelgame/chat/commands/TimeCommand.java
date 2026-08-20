package com.voxelgame.chat.commands;

/**
 * /time <day|night|noon|set <0..24000>> — управление временем суток.
 *
 * Time values follow Minecraft convention: 0 = dawn, 6000 = noon,
 * 12000 = dusk, 18000 = midnight.
 */
public class TimeCommand implements Command {

    /** Interface to read and write the world clock. */
    public interface TimeAccessor {
        double getTime();
        void setTime(double time);
    }

    public static TimeAccessor accessor;

    @Override
    public String getName() { return "time"; }

    @Override
    public String getUsage() { return "/time <day|night|noon|set <value>>"; }

    @Override
    public String getDescription() { return "управление временем суток"; }

    @Override
    public String execute(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("укажи: day, night, noon или set <value>");
        }

        if (accessor == null) {
            return "Нет активного мира.";
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "day":
                accessor.setTime(1000.0 / 24000.0);
                return "Установлено: день (1000)";
            case "noon":
                accessor.setTime(6000.0 / 24000.0);
                return "Установлено: полдень (6000)";
            case "night":
                accessor.setTime(13000.0 / 24000.0);
                return "Установлено: ночь (13000)";
            case "midnight":
                accessor.setTime(18000.0 / 24000.0);
                return "Установлено: полночь (18000)";
            case "dawn":
            case "sunrise":
                accessor.setTime(0);
                return "Установлено: рассвет (0)";
            case "set":
                if (args.length < 2) {
                    throw new IllegalArgumentException("укажи значение времени (0-24000)");
                }
                try {
                    double value = Double.parseDouble(args[1]);
                    // Convert Minecraft ticks (0-24000) to 0..1 fraction
                    accessor.setTime(value / 24000.0);
                    return String.format("Время: %.0f (%.3f дня)", value, value / 24000.0);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("значение должно быть числом");
                }
            case "query":
            case "get":
                // Convert 0..1 back to Minecraft ticks
                double ticks = accessor.getTime() * 24000.0;
                return String.format("Текущее время: %.0f (%.3f дня)", ticks, accessor.getTime());
            default:
                throw new IllegalArgumentException("неизвестная подкоманда '" + sub + "'");
        }
    }
}
