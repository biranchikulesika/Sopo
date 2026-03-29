import yt_dlp
import os
import re
import traceback
import json

def get_video_info(url):
    try:
        ydl_opts = {
            'quiet': True,
            'no_warnings': True,
            'extract_flat': 'in_playlist',
            'skip_download': True,
        }
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            
            video_data = {
                'status': 'success',
                'title': info.get('title', 'Unknown Title'),
                'thumbnail': info.get('thumbnail', ''),
                'channel': info.get('uploader', 'Unknown Channel'),
                'duration': info.get('duration', 0),
                'is_playlist': 'entries' in info,
                'video_formats': [],
                'audio_formats': []
            }

            if video_data['is_playlist']:
                video_data['playlist_count'] = len(info.get('entries', []))
                return video_data

            formats = info.get('formats', [])
            
            # Extract Video Formats
            res_map = {} 
            for f in formats:
                height = f.get('height')
                if height and height in [360, 480, 720, 1080, 1440, 2160]:
                    acodec = f.get('acodec')
                    has_audio = acodec is not None and acodec != 'none'
                    
                    if height not in res_map:
                        res_map[height] = f
                    else:
                        curr_has_audio = res_map[height].get('acodec') not in [None, 'none']
                        if (has_audio and not curr_has_audio):
                            res_map[height] = f
                        elif (has_audio == curr_has_audio) and (f.get('tbr', 0) > res_map[height].get('tbr', 0)):
                            res_map[height] = f

            for height in sorted(res_map.keys(), reverse=True):
                f = res_map[height]
                label = f"{height}p"
                if height >= 2160: label = "4K (Ultra HD)"
                elif height >= 1440: label = "1440p (QHD)"
                elif height >= 1080: label = "1080p (FHD)"
                elif height >= 720: label = "720p (HD)"
                elif height == 480: label = "480p (Low Data)"
                
                fs = f.get('filesize') or f.get('filesize_approx') or 0
                
                video_data['video_formats'].append({
                    'label': label,
                    'format_id': f['format_id'],
                    'height': height,
                    'filesize': fs,
                    'has_audio': (f.get('acodec') not in [None, 'none'])
                })

            # Extract Audio Formats
            audio_map = {}
            for f in formats:
                if f.get('vcodec') == 'none':
                    abr = f.get('abr')
                    if abr:
                        abr_int = int(abr)
                        if abr_int not in audio_map or f.get('tbr', 0) > audio_map[abr_int].get('tbr', 0):
                            audio_map[abr_int] = f
            
            if audio_map:
                for abr in sorted(audio_map.keys(), reverse=True):
                    f = audio_map[abr]
                    fs = f.get('filesize') or f.get('filesize_approx') or 0
                    video_data['audio_formats'].append({
                        'label': f"{abr} kbps",
                        'format_id': f['format_id'],
                        'filesize': fs
                    })
            else:
                video_data['audio_formats'].append({'label': 'Best Quality', 'format_id': 'bestaudio/best', 'filesize': 0})

            return video_data
    except Exception as e:
        return {'status': 'error', 'message': str(e)}

def format_bytes(b):
    if b is None or b == 0: return "Unknown"
    for unit in ['B', 'KB', 'MB', 'GB']:
        if b < 1024: return f"{b:.1f} {unit}"
        b /= 1024
    return f"{b:.1f} TB"

def download(url, options_json, save_path, progress_callback):
    opts = json.loads(options_json)
    downloaded_files = []

    def my_hook(d):
        if d['status'] == 'downloading':
            p_str = d.get('_percent_str', '0%')
            p_clean = re.sub(r'\x1b\[[0-9;]*m', '', p_str).replace('%', '').strip()
            try:
                p_val = float(p_clean) if p_clean and p_clean != 'Unknown' else 0.0
            except:
                p_val = 0.0
                
            total = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
            downloaded = d.get('downloaded_bytes') or 0
            
            progress_data = {
                'status': 'downloading',
                'percentage': str(p_val),
                'speed_str': re.sub(r'\x1b\[[0-9;]*m', '', d.get('_speed_str', '0B/s')),
                'eta_str': re.sub(r'\x1b\[[0-9;]*m', '', d.get('_eta_str', '00:00')),
                'total_bytes': total,
                'downloaded_bytes': downloaded,
                'total_str': format_bytes(total),
                'downloaded_str': format_bytes(downloaded)
            }
            if progress_callback:
                try:
                    progress_callback.onProgress(progress_data)
                except:
                    pass
                    
        elif d['status'] == 'finished':
            fn = d.get('filename')
            if fn and fn not in downloaded_files:
                downloaded_files.append(fn)

    base_opts = {
        'progress_hooks': [my_hook],
        'outtmpl': os.path.join(save_path, '%(title)s.%(ext)s'),
        'noplaylist': True,
        'merge_output_format': None, 
        'fixup': 'never',
        'quiet': True,
        'no_warnings': True,
        'concurrent_fragment_downloads': 8,
        'socket_timeout': 30,
        'retries': 10,
    }

    try:
        if opts['type'] == 'video':
            video_opts = base_opts.copy()
            # FIX: Only download the video format. Do NOT use '+' here as it triggers an internal ffmpeg check.
            video_opts['format'] = opts['format_id']
            
            with yt_dlp.YoutubeDL(video_opts) as ydl:
                info = ydl.extract_info(url, download=True)
                acodec = info.get('acodec')
                has_audio = acodec is not None and acodec != 'none'
            
            # If the selected video format doesn't have audio, download audio separately.
            if not has_audio:
                audio_opts = base_opts.copy()
                audio_opts['format'] = 'bestaudio[ext=m4a]/bestaudio'
                with yt_dlp.YoutubeDL(audio_opts) as ydl:
                    ydl.download([url])
        else:
            audio_opts = base_opts.copy()
            audio_opts['format'] = opts.get('format_id', 'bestaudio/best')
            with yt_dlp.YoutubeDL(audio_opts) as ydl:
                ydl.download([url])
        
        final_files = [f for f in downloaded_files if os.path.exists(f)]
        video_exts = ['.mp4', '.webm', '.mkv', '.avi', '.flv', '.mov']
        final_files.sort(key=lambda x: 0 if any(x.lower().endswith(ext) for ext in video_exts) else 1)

        return {'status': 'success', 'files': final_files}
    except Exception as e:
        return {'status': 'error', 'message': f"{str(e)}\n{traceback.format_exc()}"}
