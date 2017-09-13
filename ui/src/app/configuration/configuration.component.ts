import {Component, OnDestroy, OnInit} from '@angular/core';
import {LifterService} from "../lifter.service";
import {ToolbarAction, ToolbarService} from "../toolbar/toolbar.service";

@Component({
  selector: 'app-configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.scss'],
})
export class ConfigurationComponent implements OnInit, OnDestroy {

  text: string = '';
  options: any = {maxLines: 1000, printMargin: false};

  constructor(private lifter: LifterService, private toolbar: ToolbarService) {
  }

  ngOnInit() {
    this.lifter.getConfiguration().subscribe((text) => this.text = text);
    this.toolbar.actions.next([
      new ToolbarAction(this, 'autorenew', 'Reload from Configuration', ($this) => $this.refresh()),
      new ToolbarAction(this, 'cloud_download', 'Download from KV Store', ($this) => $this.load()),
      new ToolbarAction(this, 'cloud_upload', 'Upload to KV Store', ($this) => $this.store()),
    ]);
  }

  onChange() {
    this.set();
  }

  ngOnDestroy(): void {
    this.set();
  }

  refresh() {
    console.log(this.text);
  }

  load() {
    console.log(this.text);
  }

  store() {
    console.log(this.text);
  }

  private set(): void {
    this.toolbar.progressStart();
    this.lifter.setConfiguration(this.text).subscribe(() => this.toolbar.progressStop(), () => this.toolbar.progressStop());
  }
}
