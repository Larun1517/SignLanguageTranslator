import socket
import time
import numpy as np
import tensorflow as tf
from multiprocessing import Process



host = '211.114.77.237' # 호스트 ip를 적어주세요
port = 8080            # 포트번호를 임의로 설정해주세요

actions = ['1시','2시','3시','4시','5시','6시','7시','8시','9시','10시','11시','12시',
           '오후','오전','만나자','내일','오늘','좋아','나','너','바쁘다','카페','안돼','왜','전화받다','전화하다','안녕']

word_times = ['1시','2시','3시','4시','5시','6시','7시','8시','9시','10시','11시','12시']
word_place = ['카페']

seq_length = 15



learningModel = "D:\model_mb6.tflite"
print("\n * Load Model : " + learningModel +"\n")
interpreter = tf.lite.Interpreter(model_path=learningModel)
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

seq = []
action_seq = []
sentence = []
word = "?"
term = time.time()





#multiprocessing용 추론함수
def predict(seq):
    #print('predict start.')
    
    input_data = np.expand_dims(np.array(seq[-seq_length:], dtype=np.float32), axis=0) #input데이터 정의

    interpreter.set_tensor(input_details[0]['index'], input_data)
    
    interpreter.invoke() #모델 추론

    output_data = interpreter.get_tensor(output_details[0]['index'])
    
    od = output_data.squeeze()
    
    i_pred = int(np.argmax(od))

    conf = od[i_pred]


    
    if conf < 0.9:
        #print('- below standard.')
        word = ""
        return
    
    #print('- pass standard.')


    # 분석 결과
    action = actions[i_pred]
    action_seq.append(action)


    if len(action_seq) < 3:
        return

    # 결과 검증
    this_action = '?'
    if action_seq[-1] == action_seq[-2] == action_seq[-3]:
        this_action = action
        action_seq.clear()


    #action_seq에 동작5개 초과해서 쌓이면 맨 앞에 들어간 동작 제거
    if 3 < len(action_seq):
        action_seq.pop(0)

    if this_action in word_times:
        this_action = this_action + '에'
    elif this_action in word_place:
        this_action = this_action + '에서'


    word = this_action + "#"
    client_sock.send(str(word).encode("utf-8"))
    print(' * send :', word)






server_sock = socket.socket(socket.AF_INET)
server_sock.bind((host, port))

while True:
    server_sock.listen(1)
    print("Waiting client...")
    client_sock, addr = server_sock.accept()

    if client_sock:
            print('Connected by', addr)
            
            rcv_data = client_sock.recv(1024)
            decoded = rcv_data.decode("utf-8")

            while decoded[-1] != "#" :
                rcv_data = client_sock.recv(8192)
                decoded += rcv_data.decode("utf-8")
                
            print('\n * rcv :', decoded)

            while rcv_data:
                rcv_data = client_sock.recv(8192)
                decoded = rcv_data.decode("utf-8")
                try:
                    while decoded[-1] != "#" :
                        rcv_data = client_sock.recv(8192)
                        decoded += rcv_data.decode("utf-8")
                except:
                    if len(decoded) < 1:
                        print('#### connection error ####')
                        connection = False
                        break

                # stop# 들어오면 탈출
                if decoded == "stop#":
                    connection = False
                    break
                
                print(' # recieve data.')
                if time.time() - term > 2.5:
                    seq = []
                term = time.time()

                
                decoded = decoded[:-1]
                split_data = decoded.split("/")

                #안드로이드에서 데이터 몇 개씩 묶어 보냈는가?
                split_count = 10

                #데이터 전처리 과정, 앱에서 n개씩 묶어보냈다면 n번 반복
                for rcvindex in range(split_count):
                    print('processing.')

                    lm = split_data[rcvindex].split(",")

                    joint = np.zeros((22, 3))

                    #x,y,z
                    try:
                        for i in range(21):
                            j = (i*3)
                            joint[i] = [lm[j], lm[j+1], lm[j+2]]
                    except:
                        print('#### packet delay error ####')
                        continue



                    # 전처리 시작
                    v1 = joint[[21,0,1,2,3,0,5,6,7,0,9,10,11,0,13,14,15,0,17,18,19], :3] 
                    v2 = joint[[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20], :3] 
                    v = v2 - v1

                    v = v / np.linalg.norm(v, axis=1)[:, np.newaxis]

                    angle = np.arccos(np.einsum('nt,nt->n',
                        v[[0,1,2,3,5,6,7,9,10,11,13,14,15,17,18,19],:], 
                        v[[1,2,3,4,6,7,8,10,11,12,14,15,16,18,19,20],:])) # [15,]

                    angle = np.degrees(angle)

                    d = np.concatenate([joint.flatten(), angle])

                    seq.append(d)



                    #전처리값 10개 미만이면 위 반복
                    if len(seq) < seq_length:
                        continue

                    #동작 추론 및 앱으로 데이터 전송 multiprocessing
                    if __name__ == '__main__':
                        predict(seq)

                    #seq에 전처리값이 10개 초과해서 쌓이면 맨 앞에 들어간 전처리값 제거
                    if seq_length < len(seq):
                        seq.pop(0)
            

            # 클라이언트 소켓 닫기
            if connection == False:
                client_sock.close
                print('Disconnected from', addr)

            
            #client_sock.send("endprocess".encode("utf-8"))
            #print('send : endprocess')

client_sock.close()
server_sock.close()
