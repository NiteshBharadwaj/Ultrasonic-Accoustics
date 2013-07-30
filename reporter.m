function reporter(filenam)
    [p1,fp1] = wavread(filenam);
    %fp1 = 44100;
    fp11 = 20000;
    fp12 = 20130; 
    %[B,A] = butter(10,fp11*2/fp1,'high');
    %[B,A] = butter(4,[fp11*2/fp1 fp12*2/fp1]);
    [z,p,k] = butter(10,[fp11*2/fp1 fp12*2/fp1]);
    [sos,g] = zp2sos(z,p,k);
    Hd = dfilt.df2tsos(sos,g);
    %phasedelay(Hd);
    %groupdelay(Hd);
    %Hd = dfilt.df2(B,A);
    %yp1 = filter(B,A,p1);
    Tp1 = 0;
    dp1t = 1/fp1;
    window = 9.9;
    Tp2 = Tp1+window;
    tp1 = Tp1+dp1t:dp1t:Tp2;
    %p1 = sin(tp1*2*pi*fp11)*0;
    %i=300000;
    %while i~=300882
     %   p1(i) = sin(i*2*pi*19100)+sin(i*2*pi*18100)+sin(i*2*pi*1000);
      %  i = i+1;
    %end
    yp1 = filter(Hd,p1);

    p1 = p1(fp1*Tp1+1:fp1*Tp2);
    yp1 = yp1(fp1*Tp1+1:fp1*Tp2);

    
    [Xp1,Fp11] = sft_mag(p1,fp1);
    [Yp1,Fp12] = sft_mag(yp1,fp1);
   f1 = 19000;
f2 = 19200;
[z,p,k] = butter(10,[f1*2/fp1 f2*2/fp1]);
[sos,g] = zp2sos(z,p,k);
Hd = dfilt.df2tsos(sos,g);
y1 = filter(Hd,p1);
[Y1,F2] = sft_mag(y1,fp1);
subplot(2,3,1); plot(tp1,p1); xlabel('time');ylabel('signal');
subplot(2,3,2); plot(tp1,yp1); xlabel('time');ylabel('signal through filter1');
subplot(2,3,3); plot(tp1,y1); xlabel('time');ylabel('signal through filter2');
subplot(2,3,4); plot(Fp11,Xp1); xlabel('frequency');ylabel('Magnitude');
subplot(2,3,5); plot(Fp12,Yp1); xlabel('frequency');ylabel('Magnitude through filter1');
subplot(2,3,6); plot(F2,Y1); xlabel('frequency');ylabel('Magnitude through filter2');
end
