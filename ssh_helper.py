#!/usr/bin/env python3
"""
Helper to run commands on fux Pi via SSH
"""
import paramiko
import sys
import time

def ssh_command(command, print_output=True):
    """Execute command on fux Pi"""
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    
    try:
        client.connect('fux.local', username='pi', password='fux')
        stdin, stdout, stderr = client.exec_command(command)
        
        if print_output:
            output = stdout.read().decode()
            error = stderr.read().decode()
            if output:
                print(output)
            if error:
                print(error, file=sys.stderr)
        
        exit_status = stdout.channel.recv_exit_status()
        client.close()
        return exit_status
        
    except Exception as e:
        print(f"SSH Error: {e}", file=sys.stderr)
        return 1

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: ssh_helper.py 'command'")
        sys.exit(1)
    
    cmd = sys.argv[1]
    exit_code = ssh_command(cmd)
    sys.exit(exit_code)
