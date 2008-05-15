<?php

if($method=="full")
{
$color_schemes['file']=array();
$color_schemes['title']=array();
//$color_schemes['design']=array();
$color_schemes['table_all']=array();
$color_schemes['td_all']=array();
$color_schemes['table_messages']=array();
$color_schemes['td_messages']=array();
$color_schemes['td_userinfo']=array();

$hdl=opendir($dir_where_colors);
while($file=readdir($hdl))
	{
	if(strstr($file,".col")==true)		
		{
		$color_schemes['file'][]=substr($file,0,-strlen(".col"));
		$file1=file($dir_where_colors."/".$file);
		$file1=implode("",$file1);
		$file1=explode("[}={]",$file1);
		$color_schemes['title'][]=$file1[0];
		/*
		$color_schemes['design'][]=$file1[1];
		$color_schemes['table_all'][]=$file1[2];
		$color_schemes['td_all'][]=$file1[3];
		$color_schemes['table_messages'][]=$file1[4];
		$color_schemes['td_messages'][]=$file1[5];
		$color_schemes['td_userinfo'][]=$file1[6];
		*/
		}
	}
}
elseif($method=="lite")
{
$file1=file($dir_where_colors."/".$HTTP_COOKIE_VARS['forum_color'].".col");
$file1=implode("",$file1);
$file1=explode("[}={]",$file1);
$design=$file1[1];
$table_all=$file1[2];
$td_all=$file1[3];
$table_messages=$file1[4];
$td_messages=$file1[5];
$td_userinfo=$file1[6];
$input_button=$file1[7];
$input_text=$file1[8];
}
?>