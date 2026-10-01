<?php
declare(strict_types=1);

ini_set('display_errors','0'); ini_set('log_errors','0');
header('Cache-Control: no-store, max-age=0');
header('Referrer-Policy: no-referrer');
header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');

require_once __DIR__.'/config.php';
require_once __DIR__.'/lib/stats.php';

$cfg = securebridge_config();
$storage = rtrim((string)($cfg['storage_dir'] ?? (__DIR__.'/data')), '/\\');
stats_set_storage_dir($storage);
$userExpected = (string)($cfg['admin_username'] ?? 'admin');
$hash = (string)($cfg['admin_password_hash'] ?? '');

if ($hash === '') {
    http_response_code(503); header('Content-Type: text/plain; charset=utf-8');
    echo "Administration désactivée : configurez admin_password_hash dans config.local.php ou l'environnement.\n";
    exit;
}
$user = (string)($_SERVER['PHP_AUTH_USER'] ?? '');
$password = (string)($_SERVER['PHP_AUTH_PW'] ?? '');
if (!hash_equals($userExpected, $user) || !password_verify($password, $hash)) {
    header('WWW-Authenticate: Basic realm="Statistiques globales"');
    http_response_code(401); echo 'Authentification requise'; exit;
}

$s=stats_read();$q=queue_snapshot();$days=$s['daily']??[];ksort($days);$days=array_slice($days,-14,null,true);$tot=$s['totals']??[];$rate=($tot['messages_put']??0)>0?round(100*($tot['messages_taken']??0)/max(1,$tot['messages_put']),1):0;
function n($v): string{return number_format((int)$v,0,',',' ');}function b($v): string{$v=(int)$v;$u=['o','Kio','Mio','Gio'];$i=0;while($v>=1024&&$i<3){$v/=1024;$i++;}return round($v,$i?1:0).' '.$u[$i];}
?><!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Statistiques globales</title><style>body{margin:0;background:#0c0e10;color:#f2f5f7;font-family:system-ui,sans-serif}.w{max-width:1000px;margin:auto;padding:24px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px}.c{background:#15191d;border:1px solid #2a3138;border-radius:16px;padding:18px}.n{font-size:30px;font-weight:800}.m{color:#9ca8b3;font-size:13px}.bars{display:flex;align-items:end;gap:7px;height:170px;margin-top:16px}.bar{flex:1;min-width:12px;background:#a8c7fa;border-radius:5px 5px 0 0;position:relative}.bar span{position:absolute;bottom:-30px;left:50%;transform:translateX(-50%);font-size:9px;color:#9ca8b3;white-space:nowrap}table{width:100%;border-collapse:collapse}td,th{padding:9px;border-bottom:1px solid #2a3138;text-align:right}td:first-child,th:first-child{text-align:left}h1,h2{margin-top:0}</style></head><body><div class="w"><h1>Statistiques globales</h1><p class="m">Agrégats uniquement. Aucun utilisateur, IP, contact, groupe, appareil ou historique individuel.</p><div class="grid"><div class="c"><div class="n"><?=n($tot['installs']??0)?></div><div class="m">installations enregistrées</div></div><div class="c"><div class="n"><?=n($tot['vaults_created']??0)?></div><div class="m">coffres créés</div></div><div class="c"><div class="n"><?=n($tot['messages_put']??0)?></div><div class="m">messages relayés</div></div><div class="c"><div class="n"><?=$rate?> %</div><div class="m">récupérés / relayés</div></div><div class="c"><div class="n"><?=n($q['count'])?></div><div class="m">blobs en attente · <?=b($q['bytes'])?></div></div><div class="c"><div class="n"><?=n($tot['messages_expired']??0)?></div><div class="m">blobs expirés</div></div></div><div class="c" style="margin-top:14px"><h2>Messages relayés · 14 jours</h2><?php $mx=1;foreach($days as$d)$mx=max($mx,(int)($d['messages_put']??0));?><div class="bars"><?php foreach($days as$day=>$d):$v=(int)($d['messages_put']??0);?><div class="bar" title="<?=htmlspecialchars($day)?> : <?=$v?>" style="height:<?=max(2,round(140*$v/$mx))?>px"><span><?=htmlspecialchars(substr($day,5))?></span></div><?php endforeach;?></div></div><div class="c" style="margin-top:46px"><h2>Versions SecureBridge</h2><table><tr><th>Version</th><th>Installations</th></tr><?php foreach(($s['versions']??[])as$v=>$count):?><tr><td><?=htmlspecialchars((string)$v)?></td><td><?=n($count)?></td></tr><?php endforeach;?></table></div></div></body></html>
