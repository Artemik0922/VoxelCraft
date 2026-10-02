package com.voxelgame.chat.commands;

import com.voxelgame.chat.commands.Command;
import com.voxelgame.world.save.WorldMeta;
import com.voxelgame.world.save.Waypoint;

/**
 * /waypoint — управление точками на миникарте.
 * add [имя] | list | remove <имя> | clear
 */
public class WaypointCommand implements Command {

    /** Access to the current world metadata — set by Game. */
    public static WorldMeta meta;
    /** Current player position for /waypoint add. */
    public static org.joml.Vector3f playerPos;

    @Override
    public String getName() { return "waypoint"; }

    @Override
    public String getUsage() { return "/waypoint add [имя] | list | remove <имя> | clear"; }

    @Override
    public String getDescription() { return "точки на миникарте"; }

    @Override
    public String execute(String[] args) {
        if (meta == null) return "Нет активного мира.";
        String sub = args.length > 0 ? args[0].toLowerCase() : "list";

        switch (sub) {
            case "add" -> {
                if (playerPos == null) return "Нет активного игрока.";
                String name = args.length > 1
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                    : "Точка " + (meta.waypoints.size() + 1);
                int color = WorldMeta.WAYPOINT_COLORS[
                    meta.waypoints.size() % WorldMeta.WAYPOINT_COLORS.length];
                int x = (int) Math.floor(playerPos.x);
                int y = (int) Math.floor(playerPos.y);
                int z = (int) Math.floor(playerPos.z);
                meta.addWaypoint(name, x, y, z, color);
                return "Точка добавлена: " + name + " (" + x + ", " + z + ")";
            }
            case "remove", "rm" -> {
                if (args.length < 2) return "Использование: /waypoint remove <имя>";
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                return meta.removeWaypoint(name)
                    ? "Точка удалена: " + name
                    : "Точка не найдена: " + name;
            }
            case "clear" -> {
                int n = meta.waypoints.size();
                meta.clearWaypoints();
                return "Удалено точек: " + n;
            }
            default -> {
                if (meta.waypoints.isEmpty()) return "Точек нет. /waypoint add [имя]";
                StringBuilder sb = new StringBuilder("Точки (" + meta.waypoints.size() + "):");
                for (Waypoint w : meta.waypoints) {
                    sb.append("\n  ").append(w.name)
                      .append(" — ").append(w.x).append(", ").append(w.z);
                }
                return sb.toString();
            }
        }
    }
}
