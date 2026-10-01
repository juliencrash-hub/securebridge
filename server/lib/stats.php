<?php
declare(strict_types=1);

$GLOBALS['SECUREBRIDGE_STATS_DIR'] = __DIR__ . '/../data';

function stats_set_storage_dir(string $dir): void {
    $GLOBALS['SECUREBRIDGE_STATS_DIR'] = rtrim($dir, '/\\');
}
function stats_path(): string { return $GLOBALS['SECUREBRIDGE_STATS_DIR'] . '/stats.json'; }
function stats_default(): array {
    return [
        'totals'=>[
            'installs'=>0,'vaults_created'=>0,'messages_put'=>0,'messages_taken'=>0,
            'messages_expired'=>0,'slot_peeks'=>0
        ],
        'daily'=>[],
        'versions'=>[],
        'updated_day'=>gmdate('Y-m-d')
    ];
}
function stats_mutate(callable $fn): void {
    $path=stats_path(); @mkdir(dirname($path),0700,true);
    $fp=@fopen($path,'c+'); if(!$fp)return;
    if(!flock($fp,LOCK_EX)){fclose($fp);return;}
    $raw=stream_get_contents($fp); $s=$raw?json_decode($raw,true):null; if(!is_array($s))$s=stats_default();
    $fn($s);
    if(isset($s['daily'])&&is_array($s['daily'])&&count($s['daily'])>120){
        ksort($s['daily']); $s['daily']=array_slice($s['daily'],-120,null,true);
    }
    $s['updated_day']=gmdate('Y-m-d');
    ftruncate($fp,0); rewind($fp);
    fwrite($fp,json_encode($s,JSON_UNESCAPED_SLASHES|JSON_PRETTY_PRINT));
    fflush($fp); flock($fp,LOCK_UN); fclose($fp); @chmod($path,0600);
}
function stats_inc(string $metric,int $amount=1,?string $version=null): void {
    $allowed=['installs','vaults_created','messages_put','messages_taken','messages_expired','slot_peeks'];
    if(!in_array($metric,$allowed,true)||$amount<1)return;
    stats_mutate(function(array &$s)use($metric,$amount,$version){
        $day=gmdate('Y-m-d');
        if(!isset($s['totals'][$metric]))$s['totals'][$metric]=0; $s['totals'][$metric]+=$amount;
        if(!isset($s['daily'][$day]))$s['daily'][$day]=[];
        if(!isset($s['daily'][$day][$metric]))$s['daily'][$day][$metric]=0; $s['daily'][$day][$metric]+=$amount;
        if($metric==='installs'&&$version){
            $v=preg_replace('/[^A-Za-z0-9._+-]/','',substr($version,0,40));
            if($v!==''){if(!isset($s['versions'][$v]))$s['versions'][$v]=0;$s['versions'][$v]+=$amount;}
        }
    });
}
function stats_read(): array {
    $path=stats_path(); if(!is_file($path))return stats_default();
    $raw=@file_get_contents($path); $s=$raw?json_decode($raw,true):null;
    return is_array($s)?$s:stats_default();
}
function queue_snapshot(): array {
    $dir=$GLOBALS['SECUREBRIDGE_STATS_DIR'].'/queue';$count=0;$bytes=0;
    foreach(glob($dir.'/*/*.msg')?:[] as $f){if(is_file($f)){$count++;$bytes+=(int)@filesize($f);}}
    return ['count'=>$count,'bytes'=>$bytes];
}
