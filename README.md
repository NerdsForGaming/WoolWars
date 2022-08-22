## NOTE: This is an unofficial compilation of the plugin WoolWars by CubeCrafter
## Buy the plugin on Polymart to support him: [Link](https://polymart.org/resource/wool-wars.2551)

I would recomend you use Multiverse Core and PlaceholderAPI alongside with this plugin, It will still run without them though.

## DOWNLOAD ##
[Link](https://github.com/NerdsForGaming/WoolWars/releases/tag/1.1.2)


## 1. Update : 1.0.0
Initial Release.

## 2. Update : 1.0.1
API Changes.

## 3. Update : 1.0.2
Added /woolwars setlobby command
New interactive arena setup system (/woolwars setup)
Various bugs fixes
Added 3 new team colors (DARK_AQUA, ORANGE, PURPLE)
Changed a little the arena file structure: [Example File](https://pastebin.com/U8FmEh4U)

## 4. Update : 1.1.0
 Extended version support, Bug Fixes

    Fixed an issue related to MySQL
    Fixed play again not working properly
    Added support for all versions up to 1.19
    Added reward commands

    You have to either reset your config.yml or add the following lines:

    # Available placeholders: {player}, {uuid}
    reward-commands:
      enabled: false
      round-win:
        - "eco give {player} 100"
      round-lose:
        - "broadcast {player} has lost the round!"
      match-win: []
      match-lose: []
      kill: []
      death: []

## 5. Update : 1.1.1
 Bug Fixes

    Fixed a small scoreboard issue
    Added /woolwars join random command
    Added possibility to enable or disable the lobby scoreboard

    You have to either reset your config.yml or add the following lines.

    scoreboard:
      lobby-enabled: true
      game-enabled: true

## 6. Update : 1.1.2
 Bug Fixes

    Fixed an issue with MySQL







