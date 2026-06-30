; Inno Setup script for Samra desktop — builds Samra-Setup.exe
#define AppName "Samra"
#define AppVersion "1.0"

[Setup]
; Stable AppId — lets the installer recognize an existing Samra install and
; UPDATE it in place (same folder, same Start-menu/Desktop entry) instead of
; installing a second copy. Never change this GUID across versions.
AppId={{8F3A1C72-5D9E-4B6A-AE21-7C0E9F4D2B58}
AppName={#AppName}
AppVersion={#AppVersion}
VersionInfoVersion=1.0.0.0
AppPublisher=Samra
AppPublisherURL=
DefaultDirName={localappdata}\Programs\Samra
DefaultGroupName=Samra
DisableProgramGroupPage=yes
DisableDirPage=auto
PrivilegesRequired=lowest
; Reinstall/update into the previously chosen folder automatically.
UsePreviousAppDir=yes
; If Samra is running during an update, close it automatically (and don't
; relaunch it) so its files can be replaced without "file in use" errors.
CloseApplications=yes
RestartApplications=no
OutputDir=installer
OutputBaseFilename=Samra-Setup
SetupIconFile=samra.ico
UninstallDisplayIcon={app}\Samra.exe
UninstallDisplayName=Samra
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
WizardImageFile=installer\wiz_large.bmp,installer\wiz_large_2x.bmp
WizardSmallImageFile=installer\wiz_small.bmp,installer\wiz_small_2x.bmp,installer\wiz_small_3x.bmp
WizardImageStretch=yes

[Languages]
Name: "en"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"

[InstallDelete]
; On update: clear the old program payload first for a clean replace.
; (User settings/credentials live in %USERPROFILE%\.samra, never here.)
Type: filesandordirs; Name: "{app}\_internal"
Type: files; Name: "{app}\Samra.exe"

[Files]
Source: "dist\Samra\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\Samra"; Filename: "{app}\Samra.exe"
Name: "{userdesktop}\Samra"; Filename: "{app}\Samra.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Samra.exe"; Description: "Launch Samra now"; Flags: nowait postinstall skipifsilent

[Code]
{ One-time migration: the first Samra build had no AppId, so its uninstall key
  is "Samra_is1" rather than this version's GUID. Silently uninstall that old
  copy before installing, so the user never ends up with two entries. }
function OldUninstaller(): string;
var
  path: string;
begin
  Result := '';
  path := 'Software\Microsoft\Windows\CurrentVersion\Uninstall\Samra_is1';
  if not RegQueryStringValue(HKCU, path, 'UninstallString', Result) then
    RegQueryStringValue(HKLM, path, 'UninstallString', Result);
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  cmd: string;
  rc: Integer;
begin
  if CurStep = ssInstall then
  begin
    cmd := OldUninstaller();
    if cmd <> '' then
    begin
      cmd := RemoveQuotes(cmd);
      Exec(cmd, '/VERYSILENT /SUPPRESSMSGBOXES /NORESTART', '',
           SW_HIDE, ewWaitUntilTerminated, rc);
    end;
  end;
end;
