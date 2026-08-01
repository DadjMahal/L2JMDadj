-- AI Player Accounts Setup Script
-- Run this SQL to create AI player accounts in the L2JMobius database

USE loginserver;

-- Create AI player accounts (accessLevel 0 = normal player)
INSERT INTO accounts (login, password, accessLevel) VALUES 
('ai_combat_01', 'ai123pass', 0),
('ai_combat_02', 'ai123pass', 0),
('ai_quest_01', 'ai123pass', 0),
('ai_quest_02', 'ai123pass', 0),
('ai_merchant_01', 'ai123pass', 0),
('ai_explorer_01', 'ai123pass', 0),
('ai_social_01', 'ai123pass', 0);

-- Create characters for AI players (in gameserver database)
USE gameserver;

-- Characters linked to accounts (character_name matches account login for simplicity)
-- Fixed schema to match actual characters table structure
INSERT INTO characters (account_name, char_name, level, x, y, z, createDate, char_slot) VALUES
('ai_combat_01', 'CombatBot_01', 1, 16600, 17000, 434, NOW(), 1),
('ai_combat_02', 'CombatBot_02', 1, 16600, 17000, 434, NOW(), 2),
('ai_quest_01', 'QuestBot_01', 1, 16600, 17000, 434, NOW(), 3),
('ai_quest_02', 'QuestBot_02', 1, 16600, 17000, 434, NOW(), 4),
('ai_merchant_01', 'MerchantBot_01', 1, 16600, 17000, 434, NOW(), 5),
('ai_explorer_01', 'ExplorerBot_01', 1, 16600, 17000, 434, NOW(), 6),
('ai_social_01', 'SocialBot_01', 1, 16600, 17000, 434, NOW(), 7);

-- Grant access to auto-play for AI accounts
UPDATE accounts SET accessLevel = 255 WHERE login IN ('ai_combat_01', 'ai_combat_02', 'ai_quest_01', 'ai_quest_02', 'ai_merchant_01', 'ai_explorer_01', 'ai_social_01');