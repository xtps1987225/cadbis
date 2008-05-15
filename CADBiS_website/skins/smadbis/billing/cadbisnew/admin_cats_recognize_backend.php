<?
if(!check_auth() || $CURRENT_USER['level']<7){	
	die("Access denied!");
}
require_once(dirname(__FILE__).'/CADBiS/recognize.php');


if(isset($_GET['urlcheck']) || !empty($_GET['urlcheck']))
	die(Recognizer::recognizeByUrlCheck($_GET['url']));
else
{
	$result = "";	
	if(isset($_POST['btnSubmit'])){
		$BILL=new CBilling($GV["dbhost"],$GV["dbname"],$GV["dblogin"],$GV["dbpassword"]);
		$cats = $BILL->GetUrlCategories();	
		foreach($cats as $cat)
			$cat['keywords'] = $BILL->GetUrlCategoryKeywords($cat['cid']);
		$uswords = $BILL->GetUrlCategoriesUnsenseWords();
		$result = Recognizer::recognizeByMyself($_POST['tbUrl'], $cats, $uswords);
	}
}
