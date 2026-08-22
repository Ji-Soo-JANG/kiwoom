INSERT INTO strategy_definition(strategy_id, version, name, description, status, parameters_json)
VALUES ('drop-multi-base-current-pullback', 3, '급락-연속박스-최근회복-현재눌림',
        '급락 직후 시작된 장기 박스권이 최근 회복 직전까지 이어지고 최신 일봉이 현재 눌림 단계인 종목만 탐지한다. 과거에 완료된 패턴은 제외한다.',
        'PAPER_ENABLED',
        '{"requiredHistoryDays":1500,"baseWindows":[60,120,240,480,720,1200],"recentRecoveryLookbackDays":15,"minimumDropRate":-0.30,"minimumBoxCoverage":0.80,"minimumVolumeSpikes":2,"minimumRecoveryRatio":0.15,"maximumRecoveryRatio":0.30,"minimumPullbackRate":-0.12,"maximumPullbackRate":-0.04,"allPatternStagesRequired":true}');
