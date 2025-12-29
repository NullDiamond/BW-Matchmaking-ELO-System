# Team Balancer - Usage Guide

## Overview

The Team Balancer creates balanced teams for mega mode matches based on player ELO ratings. It uses an **iterative greedy algorithm** with controlled randomization to ensure optimal balance and team variability across multiple games.

## Input Format

Create a JSON file with a list of player UUIDs:

```json
{
  "players": [
    "00000000-0000-0000-0000-000000000001",
    "00000000-0000-0000-0000-000000000002",
    "00000000-0000-0000-0000-000000000003",
    ...
  ]
}
```

## Usage

### Command Line

```bash
mvn -q exec:java -Dexec.args="--balance path/to/players.json"
```

### Interactive Menu

1. Run the application: `mvn -q exec:java`
2. Select option `6. Balance Mega Teams`
3. Enter the path to your player list JSON file

## Algorithm

The team balancer uses an **iterative greedy algorithm with controlled randomization**:

### Phase 1: Preparation
1. Loads all player ELO ratings from the mega leaderboard
2. Sorts players by ELO (highest to lowest)
3. **Shuffles players within ELO brackets** (~100 ELO ranges) to add variability between runs

### Phase 2: Iterative Balance Optimization
1. Starts with a target maximum difference of **20 total ELO**
2. Attempts to balance teams using the greedy approach:
   - **Enforces equal team sizes** (e.g., 8v8 for 16 players, 8v9 for 17 players)
   - Assigns each player to the team with lower total ELO
   - Validates that the final total ELO difference meets the threshold
3. If the target cannot be achieved:
   - Increases threshold by 10 ELO
   - Re-shuffles players within ELO brackets
   - Tries again (up to 10 attempts)
4. Falls back to greedy algorithm without threshold if needed 

### Key Features
- **Equal Team Sizes**: Teams always have equal players, or differ by only 1 when odd numbers
- **Total ELO Balance**: Uses total team ELO for balance comparison (fairer than average when team sizes differ)
- **Controlled Randomization**: Shuffling within ELO brackets prevents repetitive teams while maintaining fairness
- **Adaptive Thresholds**: Automatically adjusts balance requirements if initial target is too strict

This approach ensures:
- ✅ Extremely tight balance (typically 10-75 total ELO difference for 16 players)
- ✅ Different team compositions across multiple games with the same players
- ✅ Fair distribution even with large ELO gaps between top and bottom players

## Output

### Console Output

The balancer displays:
- Total number of players
- Warning for any unknown players (assigned default 1200 ELO)
- Both teams with player names, ELO, and game counts
- Balance summary with:
  - **Total ELO** for each team
  - **Average ELO** per player for each team
  - **Total ELO difference** between teams
  - **Average ELO difference** per player
  - Balance quality assessment

### Output File

A JSON file is created with the suffix `_balanced.json` containing:

```json
{
  "redTeam": ["00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002", ...],
  "blueTeam": ["00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000004", ...],
  "redAvgElo": 1319.5,
  "blueAvgElo": 1328.9
}
```

## Balance Quality

The balance assessment is based on **total ELO difference** (scaled by team size):

- **< 20 x team_size total ELO**: Well balanced (e.g., < 160 for 8v8)
- **20-50 x team_size total ELO**: Reasonably balanced (e.g., 160-400 for 8v8)
- **> 50 x team_size total ELO**: May be unbalanced - consider manual adjustments

For 16 players (8v8 teams):
- **< 160 total ELO difference**: Well balanced
- **160-400 total ELO difference**: Reasonably balanced
- **> 400 total ELO difference**: May need adjustments

This scales appropriately with team size and is fairer than average ELO when teams have unequal sizes.

## Example

Input file: `data/sample_team_balance_16players.json`

```json
{
  "players": [
    "00000000-0000-0000-0000-000000000001",
    "00000000-0000-0000-0000-000000000002",
    ...
  ]
}
```

Run:
```bash
mvn -q exec:java -Dexec.args="--balance data/sample_team_balance_16players.json"
```

Output:
```
========================================
TEAM BALANCER - MEGA MODE
========================================

Total players: 16

Balancing teams with target max difference: 20.0 ELO

RED TEAM (8 players)
#    Player                    ELO          Games
1    Player_A                  1537.5       20
2    Player_B                  1317.6       20
3    Player_C                  1349.5       10
4    Player_D                  1336.9       7
5    Player_E                  1265.6       4
6    Player_F                  1255.7       6
7    Unknown-00000000          1200.0       0
8    Player_G                  1293.3       20

BLUE TEAM (8 players)
#    Player                    ELO          Games
1    Player_H                  1329.9       12
2    Player_I                  1411.7       14
3    Player_J                  1329.1       20
4    Player_K                  1366.8       10
5    Player_L                  1366.9       13
6    Player_M                  1275.8       8
7    Player_N                  1282.8       10
8    Player_O                  1268.1       5

========================================
BALANCE SUMMARY
========================================
Red Team:  8 players, Total ELO: 10556.1, Average: 1319.5
Blue Team: 8 players, Total ELO: 10631.1, Average: 1328.9
Total ELO Difference: 75.0
Average ELO Difference: 9.4
Teams are well balanced!
```

Output file: `data/sample_team_balance_16players_balanced.json`

### Multiple Runs with Same Players

Running the same input multiple times produces different team compositions due to randomization:

**Run 1:**
- Total ELO Difference: 75.0 (9.4 avg per player)
- Red: Player_A, Player_B, Player_C, Player_D, Player_E, Player_F, Unknown, Player_G

**Run 2:**
- Total ELO Difference: 10.0 (1.3 avg per player)
- Red: Player_A, Player_H, Player_J, Player_D, Player_O, Player_E, Player_M, Player_F

**Run 3:**
- Total ELO Difference: 14.2 (1.8 avg per player)
- Red: Player_A, Player_L, Player_H, Player_K, Unknown, Player_M, Player_O, Player_F

This variability prevents repetitive matchups while maintaining excellent balance.

## Notes

- Players not found in the leaderboard are assigned a default ELO of 1200
- The balancer uses the historical mega leaderboard (`leaderboards_java/mega_leaderboard.json`)
- Works best with even numbers of players (divides evenly between teams)
- For odd numbers, one team will have one more player than the other
