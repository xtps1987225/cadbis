<?php
$blocked_ips=array();
$file=get_file($vars['file_blocked_ips']);
$ip = get_ip_address();
$blocked_ips=explode("$smb",$file);
$strings=explode("$smb",$file);
for($i=0;$i<count($strings);$i++)
	{
	$parts=array();
	$parts=explode(".",$strings[$i]);
	$parts_ip=explode(".",$ip);
	$same0=false;
	$same1=false;
	$same2=false;
	$same3=false;
	if(($parts[0]=="*")||($parts[0]==$parts_ip[0]))
		{
		$same0=true;
		}
	if(($parts[1]=="*")||($parts[1]==$parts_ip[1]))
		{
		$same1=true;
		}
	if(($parts[2]=="*")||($parts[2]==$parts_ip[2]))
		{
		$same2=true;
		}
	if(($parts[3]=="*")||($parts[3]==$parts_ip[3]))
		{
		$same3=true;
		}
	if(($same0==true)&&($same1==true)&&($same2==true)&&($same3==true))
		{
		echo("<CENTER><B>".$language['ip_blocked']."</B></CENTER>");
		exit;
		}

	}
?>