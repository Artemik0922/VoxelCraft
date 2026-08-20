Set sh = CreateObject("WshShell")
sh.CurrentDirectory = "D:\minecraft-clone"
sh.Run "cmd /c java -cp ""build;lib\*"" -Djava.library.path=build\natives com.voxelgame.Main > game_out.txt 2> game_err.txt", 0, False