import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {LifterService} from "../lifter.service";
import {ToolbarAction, ToolbarService} from "../toolbar/toolbar.service";
import {MD_DIALOG_DATA, MdDialog, MdDialogRef} from "@angular/material";

@Component({
  selector: 'app-configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.scss'],
})
export class ConfigurationComponent implements OnInit, OnDestroy {

  text: string = '';
  options: any = {maxLines: 1000, printMargin: false};

  constructor(private lifter: LifterService,
              private toolbar: ToolbarService,
              private dialog: MdDialog) {
  }

  ngOnInit() {
    this.lifter.getConfiguration(false).subscribe((text) => this.text = text);
    this.toolbar.actions.next([
      new ToolbarAction(this, 'autorenew', 'Reload from Configuration', ($this) => $this.refresh()),
      new ToolbarAction(this, 'cloud_download', 'Pull from KV Store', ($this) => $this.load()),
      new ToolbarAction(this, 'cloud_upload', 'Push to KV Store', ($this) => $this.store()),
    ]);
  }

  onChange() {
    this.set();
  }

  ngOnDestroy(): void {
    this.set();
  }

  refresh() {
    const $this = this;
    this.lifter.getConfiguration(true).subscribe((text) => {
      if (text !== this.text) {
        let dialogRef = this.dialog.open(ConfigurationUpdateDialog);
        dialogRef.afterClosed().subscribe(result => {
          if (result) {
            $this.text = text;
            $this.set(true);
          }
        });
      }
    });
  }

  load() {
    console.log(this.text);
  }

  store() {
    console.log(this.text);
  }

  private set(force: boolean = false): void {
    this.toolbar.progressStart();
    this.lifter.setConfiguration(this.text, force).subscribe(
      () => this.toolbar.progressStop(),
      () => this.toolbar.progressStop(),
      () => this.toolbar.progressStop()
    );
  }
}

@Component({
  selector: 'app-configuration-dialog',
  templateUrl: './dialog.component.html',
})
export class ConfigurationUpdateDialog {

  constructor(public dialogRef: MdDialogRef<ConfigurationUpdateDialog>,
              @Inject(MD_DIALOG_DATA) public data: any) {
  }

  onNoClick(): void {
    this.dialogRef.close();
  }
}
