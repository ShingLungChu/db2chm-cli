program chmnavdump;
{$mode objfpc}{$H+}
uses {$ifdef Unix}cthreads,cwstring,{$endif} Classes,SysUtils,chmreader,chmfiftimain;
const objects:array[0..11] of string=('/#STRINGS','/#SYSTEM','/#TOCIDX',
 '/$WWKeywordLinks/BTree','/#TOPICS','/#URLTBL','/#URLSTR','/$FIftiMain','/#WINDOWS',
 '/contents.hhc','/index.hhk','/$OBJINST');
 names:array[0..11] of string=('strings.bin','system.bin','tocidx.bin',
 'keyword-btree.bin','topics.bin','urltbl.bin','urlstr.bin','search.bin','windows.bin',
 'contents.bin','index.bin','objinst.bin');
type TSink=class
 Lines:TStringList;
 procedure Found(Sender:TChmSearchReader; AWord:string; ATopic,AWordIndex:DWord);
end;
procedure TSink.Found(Sender:TChmSearchReader; AWord:string; ATopic,AWordIndex:DWord);
var s:string;i:integer;
begin
 s:='';for i:=1 to Length(AWord) do s:=s+IntToHex(Byte(AWord[i]),2);
 Lines.Add(s+#9+IntToStr(ATopic)+#9+IntToStr(AWordIndex));
end;
var reader:TChmReader; data:TMemoryStream; i:integer; sink:TSink;
begin
 if ParamCount<>2 then Halt(2);
 ForceDirectories(ParamStr(2));
 reader:=TChmReader.Create(TFileStream.Create(ParamStr(1),fmOpenRead or fmShareDenyNone),true);
 try
  if not reader.IsValidFile then Halt(3);
  for i:=0 to High(objects) do begin
   data:=reader.GetObject(objects[i]);
   if Assigned(data) then try
    data.SaveToFile(IncludeTrailingPathDelimiter(ParamStr(2))+names[i]);
   finally data.Free; end;
  end;
  data:=reader.GetObject('/$FIftiMain');
  if Assigned(data) then begin
   reader.SearchReader:=TChmSearchReader.Create(data,true);
   sink:=TSink.Create;sink.Lines:=TStringList.Create;
   try
    reader.SearchReader.DumpData(@sink.Found);
    sink.Lines.SaveToFile(IncludeTrailingPathDelimiter(ParamStr(2))+'search-words.tsv');
   finally sink.Lines.Free;sink.Free;end;
  end;
 finally reader.Free; end;
end.
