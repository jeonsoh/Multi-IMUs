## 다수의 관성 센서 협력을 통한 보행자 추측 항법
### IMUs Collaboration for Pedestrian Dead Reckoning

손준택 , 전소향, 권진세, 김형신
충남대학교 컴퓨터공학과
{thswnsxor123, jeonsohh}@naver.co.kr, {kwonse, hyungshin}@cnu.ac.kr



급속한 도시화로 인해 건물들이 서로 연결된 실내 공간이 많아지고 있다. 하지만, 실내 위치 정보 서비스들은 이러한 환경에 대응하는 서비스를 제공하지 못하고 있다. 따라서 실내 기반 시설 없이 지도만 있으면 위치 추정이 가능한 보행자 추측 항법의 위치 정확도 향상 기법을 연구했다. 
우리는 멀티 디바이스 환경을 고려하여 다수의 관성센서를 이용한 위치 보정 알고리즘 연구와 다수의 디바이스가 서버와 연동되어 최적의 관성 센서의 값을 선택하도록 하는 시스템을 개발하였다. 우리가 제안하는 알고리즘은 다수의 실험을 통해 검증하였다.



* * *


## 시스템 시나리오 

1. 파이어베이스 연동을 위해 구글 로그인

2. 로그인 후 NEW USER버튼 클릭하여 회원 아이디와 평균 보폭을 입력하여 계정 생성

3. 회원 아이디 클릭 후 그래프화면으로 이동(Calibration Cancel 누름)

4. START 버튼 클릭



- Device 1)

1. CHANGESETTING버튼을 통해 송신 디바이스로 설정

2. 초기값 전송 클릭(초기 방위값 전송)



- Device 2)

1. 초기값 받기 클릭(초기 방위값 받음)



- Device 1,2 공통)

1. 휴대폰을 손바닥위에 올려놓고 걸음 시작


