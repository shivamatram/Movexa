import sys
path=sys.argv[1]
nums=list(map(int,sys.argv[2:]))
with open(path,'rb') as f:
    lines=f.read().splitlines()
for n in nums:
    idx=n-1
    print('line',n,lines[idx])
    print([hex(b) for b in lines[idx]])
