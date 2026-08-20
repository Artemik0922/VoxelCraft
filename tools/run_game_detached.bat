@echo off
cd /d D:\minecraft-clone
java -cp "build;lib\*" -Djava.library.path=build\natives com.voxelgame.Main > game_out.txt 2> game_err.txt