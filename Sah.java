import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import javax.sound.sampled.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Sah extends Application {

    //Barve
    private static final Color SVETLO        = Color.web("#f0d9b5");
    private static final Color TEMNO         = Color.web("#b58863");
    private static final Color SVETLO_IZBRAN = Color.web("#f6f669");
    private static final Color TEMNO_IZBRAN  = Color.web("#baca2b");
    private static final Color SVETLO_ZADNJI = Color.web("#cdd16e");
    private static final Color TEMNO_ZADNJI  = Color.web("#aaa23a");
    private static final Color NAMIG_DOT     = Color.rgb(0, 0, 0, 0.18);
    private static final Color NAMIG_RING    = Color.rgb(0, 0, 0, 0.22);
    private static final Color OKVIR_BARVA   = Color.web("#3d1f00");
    private static final Color OZNAKA_BARVA  = Color.web("#c8a070");

    private static final int POLJE   = 72;
    private static final int OKVIR   = 22;
    private static final int VELIKOST = 8;

    private Polje[][] mreza = new Polje[VELIKOST][VELIKOST];
    private Polje izbranoPolje = null;
    private Polje zadnjiOd    = null;
    private Polje zadnjiNa    = null;
    private boolean beliNaVrsti = true;

    private int enPassantVrstica = -1;
    private int enPassantStolpec = -1;

    private boolean beliKraljPremikan    = false;
    private boolean crniKraljPremikan    = false;
    private boolean beliTopLevoPremikan  = false;
    private boolean beliTopDesnoPremikan = false;
    private boolean crniTopLevoPremikan  = false;
    private boolean crniTopDesnoPremikan = false;

    private boolean casovnaOmejitev = false;
    private int casBeli;
    private int casCrni;
    private javafx.animation.Timeline casovnik;
    private Label labelBeli;
    private Label labelCrni;

    private ListView<String> moveLog;
    private int stevilcPoteze = 1;

    private Deque<StanjeIgre> undoStack = new ArrayDeque<>();

    //Remi: sledenje stanju 
    // Trikratna ponovitev: ključ = FEN-like string položaja, vrednost = število pojavitev
    private Map<String, Integer> pozicijeZgodovina = new HashMap<>();
    // 50-potezno pravilo: šteje polpoteze od zadnjega jemanja ali premika kmeta
    private int polpotezaBrezJemanjaKmeta = 0;

    // start 

    @Override
    public void start(Stage stage) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Šah");
        dialog.setHeaderText("Nastavitve igre");

        CheckBox cbCas = new CheckBox("Vklopi časovnik");
        Spinner<Integer> minuteSpinner = new Spinner<>(1, 60, 10);
        minuteSpinner.setEditable(true);
        minuteSpinner.setPrefWidth(80);
        minuteSpinner.setDisable(true);
        Label lblMin = new Label(" minut na igralca");
        lblMin.setDisable(true);
        cbCas.setOnAction(e -> {
            minuteSpinner.setDisable(!cbCas.isSelected());
            lblMin.setDisable(!cbCas.isSelected());
        });
        HBox casRow = new HBox(8, minuteSpinner, lblMin);
        casRow.setAlignment(Pos.CENTER_LEFT);
        VBox vsebina = new VBox(12, cbCas, casRow);
        vsebina.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(vsebina);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> rez = dialog.showAndWait();
        if (rez.isEmpty() || rez.get() == ButtonType.CANCEL) { Platform.exit(); return; }

        casovnaOmejitev = cbCas.isSelected();
        casBeli = minuteSpinner.getValue() * 60;
        casCrni = minuteSpinner.getValue() * 60;

        GridPane sahovnica = new GridPane();
        sahovnica.setStyle("-fx-background-color: transparent;");
        for (int v = 0; v < VELIKOST; v++) {
            for (int s = 0; s < VELIKOST; s++) {
                mreza[v][s] = new Polje(v, s);
                sahovnica.add(mreza[v][s], s, v);
                final Polje p = mreza[v][s];
                p.setOnMouseClicked(e -> upravljajKlik(p));
            }
        }
        postaviZacetneFigure();

        //zabeleži začetni položaj
        zabelezPozicijo();

        int boardPx = POLJE * 8;
        int totalPx = boardPx + 2 * OKVIR;
        Pane boardPane = new Pane();
        boardPane.setPrefSize(totalPx, totalPx);

        Rectangle okvirRect = new Rectangle(totalPx, totalPx);
        okvirRect.setFill(OKVIR_BARVA);
        okvirRect.setArcWidth(6); okvirRect.setArcHeight(6);
        InnerShadow innerSh = new InnerShadow(8, Color.color(0,0,0,0.6));
        okvirRect.setEffect(innerSh);
        boardPane.getChildren().add(okvirRect);

        String[] colLabels = {"a","b","c","d","e","f","g","h"};
        Font oznFont = Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11);
        for (int s = 0; s < 8; s++) {
            double cx = OKVIR + s * POLJE + POLJE / 2.0;
            Text tBot = new Text(colLabels[s]);
            tBot.setFont(oznFont); tBot.setFill(OZNAKA_BARVA);
            tBot.setLayoutX(cx - tBot.getBoundsInLocal().getWidth() / 2 - 1);
            tBot.setLayoutY(totalPx - 5);
            boardPane.getChildren().add(tBot);
            Text tTop = new Text(colLabels[s]);
            tTop.setFont(oznFont); tTop.setFill(OZNAKA_BARVA);
            tTop.setLayoutX(cx - tTop.getBoundsInLocal().getWidth() / 2 - 1);
            tTop.setLayoutY(OKVIR - 5);
            boardPane.getChildren().add(tTop);
        }
        for (int v = 0; v < 8; v++) {
            double cy = OKVIR + v * POLJE + POLJE / 2.0 + 5;
            String num = String.valueOf(8 - v);
            Text tL = new Text(num);
            tL.setFont(oznFont); tL.setFill(OZNAKA_BARVA);
            tL.setLayoutX(OKVIR / 2.0 - tL.getBoundsInLocal().getWidth() / 2);
            tL.setLayoutY(cy);
            boardPane.getChildren().add(tL);
            Text tR = new Text(num);
            tR.setFont(oznFont); tR.setFill(OZNAKA_BARVA);
            tR.setLayoutX(totalPx - OKVIR / 2.0 - tR.getBoundsInLocal().getWidth() / 2);
            tR.setLayoutY(cy);
            boardPane.getChildren().add(tR);
        }
        sahovnica.setLayoutX(OKVIR);
        sahovnica.setLayoutY(OKVIR);
        boardPane.getChildren().add(sahovnica);

        moveLog = new ListView<>();
        moveLog.setPrefWidth(190);
        moveLog.setPrefHeight(440);
        moveLog.setStyle(
            "-fx-font-family: 'Consolas', monospace;" +
            "-fx-font-size: 13px;" +
            "-fx-background-color: #1e1e1e;" +
            "-fx-border-color: #3d1f00; -fx-border-width: 1;"
        );

        Button btnUndo = new Button("↩  Razveljavi potezo");
        btnUndo.setPrefWidth(182);
        btnUndo.setStyle(
            "-fx-background-color: #3d1f00;" +
            "-fx-text-fill: #c8a070;" +
            "-fx-font-size: 13px;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;"
        );
        btnUndo.setOnMouseEntered(e -> btnUndo.setStyle(
            "-fx-background-color: #5a2d00;" +
            "-fx-text-fill: #e8c090;" +
            "-fx-font-size: 13px;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;"
        ));
        btnUndo.setOnMouseExited(e -> btnUndo.setStyle(
            "-fx-background-color: #3d1f00;" +
            "-fx-text-fill: #c8a070;" +
            "-fx-font-size: 13px;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;"
        ));
        btnUndo.setOnAction(e -> razveljaviPotezo());

        Label lblLog = new Label("♟  Poteze");
        lblLog.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #c8a070;");

        VBox stranPanel = new VBox(8, lblLog, moveLog, btnUndo);
        stranPanel.setPadding(new Insets(10, 10, 10, 8));
        stranPanel.setAlignment(Pos.TOP_CENTER);
        stranPanel.setStyle("-fx-background-color: #1a1a1a;");

        HBox casovnikBar = null;
        if (casovnaOmejitev) {
            labelBeli = new Label();
            labelCrni = new Label();
            stilizacijaLabel(labelBeli, true);
            stilizacijaLabel(labelCrni, false);
            casovnikBar = new HBox(30, labelCrni, labelBeli);
            casovnikBar.setAlignment(Pos.CENTER);
            casovnikBar.setPadding(new Insets(8));
            casovnikBar.setStyle("-fx-background-color: #1a1a1a;");
        }

        BorderPane koren = new BorderPane();
        koren.setStyle("-fx-background-color: #1a1a1a;");
        koren.setCenter(boardPane);
        koren.setRight(stranPanel);
        BorderPane.setMargin(boardPane, new Insets(12, 4, 12, 12));
        if (casovnikBar != null) { koren.setBottom(casovnikBar); zacniCasovnik(); }

        Scene scene = new Scene(koren);
        stage.setScene(scene);
        stage.setTitle("Šah");
        stage.setResizable(false);
        stage.show();
    }

    //Časovnik

    private String formatCas(int s) { return String.format("%d:%02d", s / 60, s % 60); }

    private void stilizacijaLabel(Label l, boolean bela) {
        l.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 19));
        l.setStyle("-fx-text-fill: " + (bela ? "#f0d9b5" : "#c8a070") + ";");
        l.setText((bela ? "♔  Beli:  " : "♚  Črni:  ") + formatCas(bela ? casBeli : casCrni));
    }

    private void posodobiLabel() {
        if (labelBeli == null) return;
        labelBeli.setText("♔  Beli:  " + formatCas(casBeli));
        labelCrni.setText("♚  Črni:  " + formatCas(casCrni));
    }

    private void zacniCasovnik() {
        casovnik = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> {
                if (beliNaVrsti) {
                    casBeli--;
                    if (casBeli <= 0) { casBeli=0; posodobiLabel(); casovnik.stop(); new Alert(Alert.AlertType.INFORMATION,"Beli je ostal brez časa!\nČrni zmaga!").showAndWait(); return; }
                } else {
                    casCrni--;
                    if (casCrni <= 0) { casCrni=0; posodobiLabel(); casovnik.stop(); new Alert(Alert.AlertType.INFORMATION,"Črni je ostal brez časa!\nBeli zmaga!").showAndWait(); return; }
                }
                posodobiLabel();
            })
        );
        casovnik.setCycleCount(javafx.animation.Animation.INDEFINITE);
        casovnik.play();
    }

    //Zvok

    private void predvajajZvok(boolean jemanje) {
        new Thread(() -> {
            try {
                float sr = 44100f;
                int n = jemanje ? 5000 : 2000;
                byte[] buf = new byte[n];
                double freq = jemanje ? 260.0 : 430.0;
                for (int i = 0; i < n; i++) {
                    double env = Math.exp(-i / (n / 4.0));
                    buf[i] = (byte)(env * 90 * Math.sin(2 * Math.PI * freq * i / sr));
                }
                AudioFormat fmt = new AudioFormat(sr, 8, 1, true, false);
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt));
                line.open(fmt); line.start(); line.write(buf, 0, n); line.drain(); line.close();
            } catch (Exception ignored) {}
        }).start();
    }

    //Klik 

    private void upravljajKlik(Polje kliknjeno) {
        if (izbranoPolje == null) {
            if (!kliknjeno.jePrazno && kliknjeno.jeBelaFigura == beliNaVrsti) {
                izbranoPolje = kliknjeno;
                pocistiOznake();
                prikaziMoznePoteze(izbranoPolje);
                kliknjeno.oznaciIzbrano(true);
            }
        } else {
            if (preveriVeljavnostPoteze(izbranoPolje, kliknjeno, true)) {
                shraniStanje();
                boolean jeJemanje = !kliknjeno.jePrazno ||
                    (izbranoPolje.tipFigure.equals("P") && Math.abs(kliknjeno.stolpec-izbranoPolje.stolpec)==1 && kliknjeno.jePrazno);
                String notacija = generirajNotacijo(izbranoPolje, kliknjeno);
                Polje odRef = izbranoPolje;

                // posodobi števec za 50-potezno pravilo pred premikom
                boolean jeKmet = izbranoPolje.tipFigure.equals("P");
                if (jeJemanje || jeKmet) {
                    polpotezaBrezJemanjaKmeta = 0;
                } else {
                    polpotezaBrezJemanjaKmeta++;
                }

                izvrsiPremik(izbranoPolje, kliknjeno);

                if (zadnjiOd != null) zadnjiOd.oznaciZadnji(false);
                if (zadnjiNa != null) zadnjiNa.oznaciZadnji(false);
                zadnjiOd = odRef; zadnjiNa = kliknjeno;
                zadnjiOd.oznaciZadnji(true); zadnjiNa.oznaciZadnji(true);
                dodajVLog(notacija);
                predvajajZvok(jeJemanje);
                beliNaVrsti = !beliNaVrsti;
                izbranoPolje = null;
                pocistiOznake();

                // zabeleži nov položaj in preveri remi
                zabelezPozicijo();
                preveriKonecIgre();
            } else if (!kliknjeno.jePrazno && kliknjeno.jeBelaFigura == beliNaVrsti) {
                pocistiOznake();
                izbranoPolje = kliknjeno;
                prikaziMoznePoteze(izbranoPolje);
                kliknjeno.oznaciIzbrano(true);
            } else {
                izbranoPolje = null;
                pocistiOznake();
            }
        }
    }

    // Veljavnost poteze

    private boolean preveriVeljavnostPoteze(Polje od, Polje cilj, boolean preveriSah) {
        if (od == cilj) return false;
        if (!cilj.jePrazno && cilj.jeBelaFigura == od.jeBelaFigura) return false;

        int dv = cilj.vrstica - od.vrstica;
        int ds = cilj.stolpec - od.stolpec;
        boolean jeMogoce = false;

        switch (od.tipFigure) {
            case "P": {
                int smer = od.jeBelaFigura ? -1 : 1;
                if (ds == 0 && cilj.jePrazno) {
                    if (dv == smer) jeMogoce = true;
                    else if (dv == 2*smer && ((od.jeBelaFigura && od.vrstica==6)||(!od.jeBelaFigura && od.vrstica==1)) && jePotProsta(od,cilj)) jeMogoce = true;
                } else if (Math.abs(ds)==1 && dv==smer) {
                    if (!cilj.jePrazno) jeMogoce = true;
                    else if (cilj.vrstica==enPassantVrstica && cilj.stolpec==enPassantStolpec) jeMogoce = true;
                }
                break;
            }
            case "T": if (dv==0||ds==0) jeMogoce = jePotProsta(od,cilj); break;
            case "S": jeMogoce = (Math.abs(dv)==2&&Math.abs(ds)==1)||(Math.abs(dv)==1&&Math.abs(ds)==2); break;
            case "L": if (Math.abs(dv)==Math.abs(ds)) jeMogoce = jePotProsta(od,cilj); break;
            case "Q": if (dv==0||ds==0||Math.abs(dv)==Math.abs(ds)) jeMogoce = jePotProsta(od,cilj); break;
            case "K":
                if (Math.abs(dv)<=1 && Math.abs(ds)<=1) jeMogoce = true;
                else if (dv==0 && Math.abs(ds)==2 && preveriSah) jeMogoce = preveriRokado(od, ds>0);
                break;
        }

        if (jeMogoce && preveriSah) {
            boolean epJ = od.tipFigure.equals("P") && Math.abs(ds)==1 && cilj.jePrazno && cilj.vrstica==enPassantVrstica && cilj.stolpec==enPassantStolpec;
            boolean jeRok = od.tipFigure.equals("K") && Math.abs(ds)==2;
            String epTip=""; boolean epBela=false; Polje epPolje=null;
            if (epJ) {
                int smer = od.jeBelaFigura ? -1 : 1;
                epPolje = mreza[cilj.vrstica-smer][cilj.stolpec];
                epTip=epPolje.tipFigure; epBela=epPolje.jeBelaFigura; epPolje.jePrazno=true;
            }
            Polje topOd=null; Polje topNa=null;
            if (jeRok) {
                boolean d=ds>0; int vr=od.vrstica;
                topOd=d?mreza[vr][7]:mreza[vr][0]; topNa=d?mreza[vr][5]:mreza[vr][3];
                topNa.nastaviFiguro("T",od.jeBelaFigura); topOd.jePrazno=true;
            }
            String tt=cilj.tipFigure; boolean tb=cilj.jeBelaFigura; boolean tp=cilj.jePrazno;
            cilj.nastaviFiguro(od.tipFigure,od.jeBelaFigura); od.jePrazno=true;
            boolean vSahu = preveriAliJeSah(od.jeBelaFigura);
            od.jePrazno=false; cilj.tipFigure=tt; cilj.jeBelaFigura=tb; cilj.jePrazno=tp; cilj.osveziIzgled();
            if (epJ && epPolje!=null) epPolje.nastaviFiguro(epTip,epBela);
            if (jeRok && topOd!=null) { topOd.nastaviFiguro("T",od.jeBelaFigura); topNa.izprazniPolje(); }
            return !vSahu;
        }
        return jeMogoce;
    }

    private boolean preveriRokado(Polje kralj, boolean desna) {
        boolean bela=kralj.jeBelaFigura; int vr=kralj.vrstica;
        if (bela?beliKraljPremikan:crniKraljPremikan) return false;
        if (desna?(bela?beliTopDesnoPremikan:crniTopDesnoPremikan):(bela?beliTopLevoPremikan:crniTopLevoPremikan)) return false;
        if (desna) { if (!mreza[vr][5].jePrazno||!mreza[vr][6].jePrazno) return false; }
        else       { if (!mreza[vr][1].jePrazno||!mreza[vr][2].jePrazno||!mreza[vr][3].jePrazno) return false; }
        if (preveriAliJeSah(bela)) return false;
        int vmS=desna?5:3;
        String tt=mreza[vr][vmS].tipFigure; boolean tb=mreza[vr][vmS].jeBelaFigura; boolean tp=mreza[vr][vmS].jePrazno;
        mreza[vr][vmS].nastaviFiguro("K",bela); mreza[vr][kralj.stolpec].jePrazno=true;
        boolean napadeno=preveriAliJeSah(bela);
        mreza[vr][kralj.stolpec].jePrazno=false;
        mreza[vr][vmS].tipFigure=tt; mreza[vr][vmS].jeBelaFigura=tb; mreza[vr][vmS].jePrazno=tp; mreza[vr][vmS].osveziIzgled();
        return !napadeno;
    }

    private boolean jePotProsta(Polje od, Polje cilj) {
        int kv=Integer.compare(cilj.vrstica,od.vrstica), ks=Integer.compare(cilj.stolpec,od.stolpec);
        int tv=od.vrstica+kv, ts=od.stolpec+ks;
        while (tv!=cilj.vrstica||ts!=cilj.stolpec) { if (!mreza[tv][ts].jePrazno) return false; tv+=kv; ts+=ks; }
        return true;
    }

    private boolean preveriAliJeSah(boolean zaBele) {
        int kv=-1,ks=-1;
        outer: for (int v=0;v<8;v++) for (int s=0;s<8;s++)
            if (!mreza[v][s].jePrazno && mreza[v][s].jeBelaFigura==zaBele && mreza[v][s].tipFigure.equals("K")) { kv=v;ks=s;break outer; }
        if (kv==-1) return false;
        for (int v=0;v<8;v++) for (int s=0;s<8;s++)
            if (!mreza[v][s].jePrazno && mreza[v][s].jeBelaFigura!=zaBele)
                if (preveriVeljavnostPoteze(mreza[v][s],mreza[kv][ks],false)) return true;
        return false;
    }

    // Prikaz možnih potez 

    private void prikaziMoznePoteze(Polje od) {
        for (int v=0;v<8;v++) for (int s=0;s<8;s++)
            if (preveriVeljavnostPoteze(od,mreza[v][s],true)) mreza[v][s].poudari(true);
    }

    private void pocistiOznake() {
        for (Polje[] vr:mreza) for (Polje p:vr) { p.poudari(false); p.oznaciIzbrano(false); }
    }

    // Izvrši premik

    private void izvrsiPremik(Polje od, Polje cilj) {
        boolean jeKmet=od.tipFigure.equals("P"), jeKralj=od.tipFigure.equals("K"), jeTop=od.tipFigure.equals("T");
        int smer=od.jeBelaFigura?-1:1, dv=cilj.vrstica-od.vrstica, ds=cilj.stolpec-od.stolpec;

        boolean epJ=jeKmet&&Math.abs(ds)==1&&cilj.jePrazno&&cilj.vrstica==enPassantVrstica&&cilj.stolpec==enPassantStolpec;
        if (epJ) mreza[cilj.vrstica-smer][cilj.stolpec].izprazniPolje();

        if (jeKmet&&Math.abs(dv)==2) { enPassantVrstica=od.vrstica+smer; enPassantStolpec=od.stolpec; }
        else { enPassantVrstica=-1; enPassantStolpec=-1; }

        if (jeKralj&&Math.abs(ds)==2) {
            int vr=od.vrstica;
            if (ds>0) { mreza[vr][5].nastaviFiguro("T",od.jeBelaFigura); mreza[vr][7].izprazniPolje(); }
            else      { mreza[vr][3].nastaviFiguro("T",od.jeBelaFigura); mreza[vr][0].izprazniPolje(); }
        }

        if (jeKralj) { if (od.jeBelaFigura) beliKraljPremikan=true; else crniKraljPremikan=true; }
        if (jeTop&&od.jeBelaFigura)  { if (od.stolpec==0) beliTopLevoPremikan=true;  else if (od.stolpec==7) beliTopDesnoPremikan=true; }
        if (jeTop&&!od.jeBelaFigura) { if (od.stolpec==0) crniTopLevoPremikan=true;  else if (od.stolpec==7) crniTopDesnoPremikan=true;  }

        cilj.nastaviFiguro(od.tipFigure,od.jeBelaFigura);
        od.izprazniPolje();

        if (jeKmet&&(cilj.vrstica==0||cilj.vrstica==7))
            cilj.nastaviFiguro(prikaziPromoDialog(cilj.jeBelaFigura),cilj.jeBelaFigura);
    }

    private String prikaziPromoDialog(boolean bela) {
        ChoiceDialog<String> dlg = new ChoiceDialog<>();
        dlg.setTitle("Promocija kmeta"); dlg.setHeaderText("Kmet je dosegel konec table!"); dlg.setContentText("Izberi figuro:");
        dlg.getItems().addAll(bela?"♕ Kraljica":"♛ Kraljica",bela?"♖ Trdnjava":"♜ Trdnjava",bela?"♗ Lovec":"♝ Lovec",bela?"♘ Konj":"♞ Konj");
        dlg.setSelectedItem(dlg.getItems().get(0));
        String s=dlg.showAndWait().orElse(dlg.getItems().get(0));
        if (s.contains("Kraljica")) return "Q"; if (s.contains("Trdnjava")) return "T"; if (s.contains("Lovec")) return "L"; return "S";
    }

    //Move log 

    private static final String[] COL_LABELS = {"a","b","c","d","e","f","g","h"};

    private String generirajNotacijo(Polje od, Polje cilj) {
        if (od.tipFigure.equals("K")&&Math.abs(cilj.stolpec-od.stolpec)==2) return cilj.stolpec>od.stolpec?"O-O":"O-O-O";
        String fig=od.tipFigure.equals("P")?"":od.tipFigure;
        String odS=COL_LABELS[od.stolpec]+(8-od.vrstica), cilS=COL_LABELS[cilj.stolpec]+(8-cilj.vrstica);
        boolean jeJ=!cilj.jePrazno||(od.tipFigure.equals("P")&&Math.abs(cilj.stolpec-od.stolpec)==1&&cilj.jePrazno);
        return fig+odS+(jeJ?"x":"-")+cilS;
    }

    private void dodajVLog(String not) {
        if (beliNaVrsti) { moveLog.getItems().add(stevilcPoteze+".  "+not); }
        else { int z=moveLog.getItems().size()-1; moveLog.getItems().set(z,moveLog.getItems().get(z)+"   "+not); stevilcPoteze++; }
        moveLog.scrollTo(moveLog.getItems().size()-1);
    }

    //Razveljavi

    private void shraniStanje() {
        undoStack.push(new StanjeIgre(mreza,beliNaVrsti,enPassantVrstica,enPassantStolpec,
            beliKraljPremikan,crniKraljPremikan,beliTopLevoPremikan,beliTopDesnoPremikan,
            crniTopLevoPremikan,crniTopDesnoPremikan,casBeli,casCrni,
            moveLog.getItems().toArray(new String[0]),stevilcPoteze,
            polpotezaBrezJemanjaKmeta,                           
            new HashMap<>(pozicijeZgodovina)));                     
    }

    private void razveljaviPotezo() {
        if (undoStack.isEmpty()) return;
        if (casovnik!=null) casovnik.stop();
        StanjeIgre s=undoStack.pop();
        for (int v=0;v<8;v++) for (int c=0;c<8;c++) {
            if (s.tipi[v][c].isEmpty()) mreza[v][c].izprazniPolje(); else mreza[v][c].nastaviFiguro(s.tipi[v][c],s.bele[v][c]);
        }
        beliNaVrsti=s.beliNaVrsti; enPassantVrstica=s.epV; enPassantStolpec=s.epS;
        beliKraljPremikan=s.beliKralj; crniKraljPremikan=s.crniKralj;
        beliTopLevoPremikan=s.beliTopL; beliTopDesnoPremikan=s.beliTopD;
        crniTopLevoPremikan=s.crniTopL; crniTopDesnoPremikan=s.crniTopD;
        casBeli=s.casBeli; casCrni=s.casCrni; stevilcPoteze=s.stevilcPoteze;
        moveLog.getItems().setAll(s.logPotez);
        polpotezaBrezJemanjaKmeta = s.polpoteze;      
        pozicijeZgodovina = s.pozicijeZgodovina;        
        if (zadnjiOd!=null) { zadnjiOd.oznaciZadnji(false); zadnjiOd=null; }
        if (zadnjiNa!=null) { zadnjiNa.oznaciZadnji(false); zadnjiNa=null; }
        posodobiLabel(); pocistiOznake(); izbranoPolje=null;
        if (casovnaOmejitev) zacniCasovnik();
    }

    //Konec igre 

    private void preveriKonecIgre() {
        // najprej preveri remi (pred šah matom, ker je remi prioriteten pri patu)
        if (preveriRemi()) return;

        boolean ima=false;
        outer: for (int v=0;v<8;v++) for (int s=0;s<8;s++)
            if (!mreza[v][s].jePrazno&&mreza[v][s].jeBelaFigura==beliNaVrsti)
                for (int nv=0;nv<8;nv++) for (int ns=0;ns<8;ns++)
                    if (preveriVeljavnostPoteze(mreza[v][s],mreza[nv][ns],true)) { ima=true; break outer; }
        if (!ima) {
            if (casovnik!=null) casovnik.stop();
            String msg=preveriAliJeSah(beliNaVrsti)?"ŠAH MAT!\n"+(beliNaVrsti?"Črni":"Beli")+" zmaga!":"PAT! Remi.";
            new Alert(Alert.AlertType.INFORMATION,msg).showAndWait();
        }
    }

    // Preveri vse vrste remija

    private boolean preveriRemi() {
        // 1. Trikratna ponovitev položaja
        String trenutniKljuc = generirajKljucPozicije();
        if (pozicijeZgodovina.getOrDefault(trenutniKljuc, 0) >= 3) {
            if (casovnik!=null) casovnik.stop();
            new Alert(Alert.AlertType.INFORMATION,
                "REMI — Trikratna ponovitev položaja!").showAndWait();
            return true;
        }

        // 2. 50-potezno pravilo (100 polpotez = 50 polnih potez)
        if (polpotezaBrezJemanjaKmeta >= 100) {
            if (casovnik!=null) casovnik.stop();
            new Alert(Alert.AlertType.INFORMATION,
                "REMI — 50-potezno pravilo\n(50 potez brez jemanja ali premika kmeta)").showAndWait();
            return true;
        }

        // 3. Nezadostni material
        if (preveriNezadostniMaterial()) {
            if (casovnik!=null) casovnik.stop();
            new Alert(Alert.AlertType.INFORMATION,
                "REMI — Nezadostni material za šah mat!").showAndWait();
            return true;
        }

        return false;
    }

    // Trikratna ponovitev: zabeleži položaj ────────────────────────────

    private void zabelezPozicijo() {
        String kljuc = generirajKljucPozicije();
        pozicijeZgodovina.put(kljuc, pozicijeZgodovina.getOrDefault(kljuc, 0) + 1);
    }

    /**
     * Generaija unikatnega ključa za trenutni položaj.
     * Vsebuje: razporeditev figur + kdo je na vrsti + možnosti rokade + en passant stolpec.
     * Dva položaja sta enaka samo če so vse te lastnosti identične.
     */
    private String generirajKljucPozicije() {
        StringBuilder sb = new StringBuilder(100);
        for (int v = 0; v < 8; v++) {
            for (int s = 0; s < 8; s++) {
                Polje p = mreza[v][s];
                if (p.jePrazno) {
                    sb.append('.');
                } else {
                    // Velika črka = bela figura, mala = črna
                    char c = p.tipFigure.charAt(0);
                    sb.append(p.jeBelaFigura ? c : Character.toLowerCase(c));
                }
            }
        }
        // Kdo je na vrsti
        sb.append(beliNaVrsti ? 'W' : 'B');
        // Možnosti rokade
        sb.append(beliKraljPremikan    ? '0' : '1');
        sb.append(beliTopLevoPremikan  ? '0' : '1');
        sb.append(beliTopDesnoPremikan ? '0' : '1');
        sb.append(crniKraljPremikan    ? '0' : '1');
        sb.append(crniTopLevoPremikan  ? '0' : '1');
        sb.append(crniTopDesnoPremikan ? '0' : '1');
        // En passant stolpec (-1 če ni možen)
        sb.append(enPassantStolpec);
        return sb.toString();
    }

    //Nezadostni material

    /**
     * Vrne true če nobena stran ne more doseči šah mata.
     * Primeri nezadostnega materiala:
     *   - Kralj vs Kralj
     *   - Kralj + Konj vs Kralj
     *   - Kralj + Lovec vs Kralj
     *   - Kralj + Lovec vs Kralj + Lovec (lovca na isti barvi)
     */
    private boolean preveriNezadostniMaterial() {
        List<String> beleFigure  = new ArrayList<>();
        List<String> crneFigure  = new ArrayList<>();
        List<Integer> beleLovciBarva = new ArrayList<>(); // 0=svetlo, 1=temno polje
        List<Integer> crniLovciBarva = new ArrayList<>();

        for (int v = 0; v < 8; v++) {
            for (int s = 0; s < 8; s++) {
                Polje p = mreza[v][s];
                if (p.jePrazno || p.tipFigure.equals("K")) continue;
                if (p.jeBelaFigura) {
                    beleFigure.add(p.tipFigure);
                    if (p.tipFigure.equals("L")) beleLovciBarva.add((v + s) % 2);
                } else {
                    crneFigure.add(p.tipFigure);
                    if (p.tipFigure.equals("L")) crniLovciBarva.add((v + s) % 2);
                }
            }
        }

        int nb = beleFigure.size();
        int nc = crneFigure.size();

        // Kralj vs Kralj
        if (nb == 0 && nc == 0) return true;

        // Kralj + ena figura vs Kralj (konj ali lovec)
        if (nb == 0 && nc == 1 && (crneFigure.get(0).equals("S") || crneFigure.get(0).equals("L"))) return true;
        if (nc == 0 && nb == 1 && (beleFigure.get(0).equals("S") || beleFigure.get(0).equals("L"))) return true;

        // Kralj + Lovec vs Kralj + Lovec — samo če sta lovca na isti barvi polja
        if (nb == 1 && nc == 1
                && beleFigure.get(0).equals("L") && crneFigure.get(0).equals("L")
                && !beleLovciBarva.isEmpty() && !crniLovciBarva.isEmpty()
                && beleLovciBarva.get(0).equals(crniLovciBarva.get(0))) {
            return true;
        }

        return false;
    }

    //Začetne figure 

    private void postaviZacetneFigure() {
        String[] l={"T","S","L","Q","K","L","S","T"};
        for (int i=0;i<8;i++) {
            mreza[0][i].nastaviFiguro(l[i],false); mreza[1][i].nastaviFiguro("P",false);
            mreza[6][i].nastaviFiguro("P",true);   mreza[7][i].nastaviFiguro(l[i],true);
        }
    }

    // StanjeIgre

    static class StanjeIgre {
        String[][] tipi=new String[8][8]; boolean[][] bele=new boolean[8][8];
        boolean beliNaVrsti; int epV,epS;
        boolean beliKralj,crniKralj,beliTopL,beliTopD,crniTopL,crniTopD;
        int casBeli,casCrni; String[] logPotez; int stevilcPoteze;
        int polpoteze;                          
        Map<String,Integer> pozicijeZgodovina;  

        StanjeIgre(Polje[][] m,boolean bNV,int epV,int epS,
                   boolean bK,boolean cK,boolean bTL,boolean bTD,boolean cTL,boolean cTD,
                   int cB,int cC,String[] log,int st,
                   int polpoteze, Map<String,Integer> poz) {  
            for (int v=0;v<8;v++) for (int s=0;s<8;s++) {
                tipi[v][s]=m[v][s].jePrazno?"":m[v][s].tipFigure;
                bele[v][s]=m[v][s].jeBelaFigura;
            }
            beliNaVrsti=bNV; this.epV=epV; this.epS=epS;
            beliKralj=bK; crniKralj=cK; beliTopL=bTL; beliTopD=bTD; crniTopL=cTL; crniTopD=cTD;
            casBeli=cB; casCrni=cC; logPotez=log.clone(); stevilcPoteze=st;
            this.polpoteze=polpoteze;                  
            this.pozicijeZgodovina=poz;                 
        }
    }

    //Razred Polje 

    class Polje extends StackPane {
        int vrstica, stolpec;
        String tipFigure = "";
        boolean jeBelaFigura, jePrazno = true;

        private final Rectangle podlaga;
        private final Rectangle izbranoOverlay;
        private final Rectangle zadnjiOverlay;
        private final Rectangle namigOverlay;
        private final Label tekst;

        private boolean jeIzbrano  = false;
        private boolean jeZadnji   = false;
        private boolean jeNamig    = false;

        Polje(int v, int s) {
            vrstica=v; stolpec=s;
            setPrefSize(POLJE, POLJE);

            boolean svetlo = (v+s)%2==0;

            podlaga = new Rectangle(POLJE, POLJE);
            podlaga.setFill(svetlo ? SVETLO : TEMNO);

            izbranoOverlay = new Rectangle(POLJE, POLJE);
            izbranoOverlay.setFill(svetlo ? SVETLO_IZBRAN : TEMNO_IZBRAN);
            izbranoOverlay.setOpacity(0.85);
            izbranoOverlay.setVisible(false);

            zadnjiOverlay = new Rectangle(POLJE, POLJE);
            zadnjiOverlay.setFill(svetlo ? SVETLO_ZADNJI : TEMNO_ZADNJI);
            zadnjiOverlay.setOpacity(0.75);
            zadnjiOverlay.setVisible(false);

            namigOverlay = new Rectangle(POLJE, POLJE);
            namigOverlay.setFill(Color.TRANSPARENT);
            namigOverlay.setVisible(false);

            tekst = new Label();
            tekst.setFont(new Font("Segoe UI Symbol", 44));
            DropShadow ds = new DropShadow(4, 1, 2, Color.color(0,0,0,0.55));
            tekst.setEffect(ds);

            getChildren().addAll(podlaga, izbranoOverlay, zadnjiOverlay, namigOverlay, tekst);

            setOnMouseEntered(e -> { if (!jeIzbrano) podlaga.setOpacity(0.85); });
            setOnMouseExited(e  -> { podlaga.setOpacity(1.0); });
        }

        void nastaviFiguro(String tip, boolean bela) {
            tipFigure=tip; jeBelaFigura=bela; jePrazno=false; osveziIzgled();
        }

        void izprazniPolje() {
            tipFigure=""; jePrazno=true; tekst.setText("");
        }

        void osveziIzgled() {
            if (jePrazno) { tekst.setText(""); return; }
            String glyph;
            switch (tipFigure) {
                case "P": glyph = jeBelaFigura?"♙":"♟"; break;
                case "T": glyph = jeBelaFigura?"♖":"♜"; break;
                case "S": glyph = jeBelaFigura?"♘":"♞"; break;
                case "L": glyph = jeBelaFigura?"♗":"♝"; break;
                case "Q": glyph = jeBelaFigura?"♕":"♛"; break;
                case "K": glyph = jeBelaFigura?"♔":"♚"; break;
                default:  glyph = ""; break;
            }
            tekst.setText(glyph);
            tekst.setStyle(jeBelaFigura
                ? "-fx-text-fill: #fafafa;"
                : "-fx-text-fill: #1a1a1a;");
        }

        void oznaciIzbrano(boolean vklopi) {
            jeIzbrano = vklopi;
            izbranoOverlay.setVisible(vklopi);
        }

        void oznaciZadnji(boolean vklopi) {
            jeZadnji = vklopi;
            zadnjiOverlay.setVisible(vklopi);
        }

        void poudari(boolean vklopi) {
            jeNamig = vklopi;
            if (vklopi) {
                setStyle("-fx-background-color: transparent; -fx-padding: 0;");
                if (jePrazno) {
                    namigOverlay.setFill(new javafx.scene.paint.RadialGradient(
                        0, 0, 0.5, 0.5, 0.22, true, CycleMethod.NO_CYCLE,
                        new Stop(0, NAMIG_DOT), new Stop(1, Color.TRANSPARENT)
                    ));
                } else {
                    namigOverlay.setFill(new javafx.scene.paint.RadialGradient(
                        0, 0, 0.5, 0.5, 0.48, true, CycleMethod.NO_CYCLE,
                        new Stop(0.68, Color.TRANSPARENT),
                        new Stop(0.72, NAMIG_RING),
                        new Stop(1.0,  Color.TRANSPARENT)
                    ));
                }
                namigOverlay.setVisible(true);
            } else {
                namigOverlay.setVisible(false);
                namigOverlay.setFill(Color.TRANSPARENT);
            }
        }
    }

    public static void main(String[] args) { launch(args); }
}
