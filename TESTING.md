# Testing with Sample Data

To test the ELO calculator with the provided sample data:

1. Ensure the sample files are in the `data/` directory:
   - `sample_games.json` - Contains 3 sample games with different team sizes
   - `sample_players.json` - Contains fake player names for the sample UUIDs

2. Run the calculator:
   ```bash
   mvn exec:java
   ```

3. Check the output files in `data/`:
   - `zero_sum_elo_java.json` - Calculated ELO ratings
   - `leaderboards_java/` - Mode-specific leaderboards
   - `invalid_games_java.json` - Games excluded from calculations

## Sample Data Overview

The sample data includes:
- **SAMPLE_GAME_001**: 4v4 fours game (Blue team wins)
- **SAMPLE_GAME_002**: 3v3 trio game (Green team wins)
- **SAMPLE_GAME_003**: 2v2 duo tie game

This provides a good mix of game types and outcomes for testing the ELO calculation algorithm.