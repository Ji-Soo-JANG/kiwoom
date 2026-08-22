INSERT INTO strategy_definition(strategy_id, version, name, description, status, parameters_json)
VALUES ('drop-multi-base-recovery-pullback', 2, '급락-장기박스-부분회복-눌림',
        'DB 일봉으로 단기 급락 후 수개월~수년 박스권, 간헐적 거래량 급증, 이전 낙폭의 15~30% 회복과 눌림을 탐지한다.',
        'PAPER_ENABLED',
        '{"requiredHistoryDays":1500,"baseWindows":[60,120,240,480,720,1200],"minimumDropRate":-0.30,"minimumBoxCoverage":0.80,"minimumVolumeSpikes":2,"volumeSpikeMultiple":2.5,"minimumRecoveryRatio":0.15,"maximumRecoveryRatio":0.30,"minimumPullbackRate":-0.12,"maximumPullbackRate":-0.04,"minimumScore":75}');
