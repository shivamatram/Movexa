import sys
path=sys.argv[1]
with open(path,'rb') as f:
    lines=f.read().splitlines()
keys=[b'confirm_mark_docs_valid_msg',b'confirm_mark_docs_invalid_msg',b'dashboard_offline_title',b'driver_confirm_accept_message',b'driver_home_offline',b'driver_home_today_summary',b'forgot_password_subtitle',b'logout_confirm_message',b'no_account',b'public_error_invalid_message']
for i,line in enumerate(lines):
    for k in keys:
        if k in line:
            print('found',k.decode(),'on line',i+1,':',line)
            print([hex(b) for b in line])
