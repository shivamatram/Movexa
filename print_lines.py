import sys
path=sys.argv[1]
with open(path,'rb') as f:
    data=f.read().splitlines()
for i in [14,524]:
    print('line',i+1,data[i])
    print([hex(b) for b in data[i]])
