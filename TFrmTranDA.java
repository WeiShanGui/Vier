/**
 * 采购订单
 */
package forms.pur;

import static basectrl.tools.StrToFloatDef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;
import basectrl.CorpOptions;
import basectrl.bean.LocalService;
import basectrl.buff.BufferType;
import basectrl.buff.MemoryBuffer;
import basectrl.buff.Memory_OurInfo;
import basectrl.rds.AppBean;
import basectrl.vcl.BuildRecord;
import basectrl.vcl.DBGrid;
import basectrl.vcl.DataSet;
import basectrl.vcl.Record;
import basectrl.vcl.TDate;
import basectrl.vcl.TDateTime;
import forms.base.PartClass_Record;
import forms.base.TForm;
import forms.easy.TFrmSupFirst;
import forms.shopCar.Shopping;
import forms.shopCar.Shopping_Record;
import forms.stock.Product_Record;

public class TFrmTranDA extends TForm implements BuildRecord
{

    @Override
    public String execute()
    {
        view.setFile("pur/TFrmTranDA.jsp");
        view.setExitUrl("TPur");
        view.addLeftMenu("TPur", "进货管理");
        view.setPageTitle("采购订单");

        int corpType = Memory_OurInfo.getCorpType(this).ordinal();
        if (corpType != 5)
        {
            view.addMenu("+登记采购订单", "javascript:append()");
            view.addMenu("采购明细变更查询", "TFrmTranDA.purLog");
            String device = (String) session.getAttribute("device");
            if ("ee".equals(device))
                view.addMenu("采购进度查询", "hrip:TSchPurPlan");
            else
                view.addMenu("采购进度查询", "TSchPurPlan");
        }

        try (MemoryBuffer buff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA");
                LocalService app = new LocalService(this))
        {
            String supCode = getValue(buff, "supCode");
            String supName = getValue(buff, "supName");
            if (corpType == 5 && "".equals(supCode))
            {
                TFrmSupFirst sup = new TFrmSupFirst();
                sup.init(this);
                String vineCorp = sup.getVineCorp(supCode);
                supCode = sup.getEasyDefaultBook();
                supName = session.getAttribute("name").toString();
                buff.setField("vineCorp", vineCorp);
                buff.setField("supCode", supCode);
                buff.setField("supName", supName);
            }
            String tbNo = getValue(buff, "tbNo");
            String dateFrom = getValue(buff, "dateFrom");
            String dateTo = getValue(buff, "dateTo");
            String appUser = getValue(buff, "appUser");
            String appUserName = getValue(buff, "appUserName");
            String manageNo = getValue(buff, "manageNo");
            String status = getValue(buff, "status");
            String temp = getValue(buff, "approval");
            boolean approval = "".equals(temp) || "true".equals(temp);

            if (tbNo.trim().isEmpty())
                tbNo = "DA*";

            if (dateFrom.trim().isEmpty())
            {
                if (corpType == 5)
                    dateFrom = TDateTime.Now().monthBof().getDate();
                else
                    dateFrom = TDateTime.Now().getDate();
                buff.setField("dateFrom", dateFrom);
            }
            if (dateTo.trim().isEmpty())
            {
                if (corpType == 5)
                    dateTo = TDateTime.Now().monthEof().getDate();
                else
                    dateTo = TDateTime.Now().getDate();
                buff.setField("dateTo", dateTo);
            }

            String TBDate_From = TDateTime.fromDate(dateFrom).toString();
            String TBDate_To = TDateTime.fromDate(dateTo).toString();

            view.add("dateFrom", dateFrom);
            view.add("dateTo", dateTo);
            view.add("supName", supName);
            view.add("appUserName", appUserName);
            view.add("approval", approval);
            view.add("tbNo", tbNo);
            view.add("price", this.getAP(supCode));

            if (corpType == 5)
            {
                app.setService("TAppEasy.getPurH");
                Record head = app.getDataIn().getHead();
                head.setField("TBNo_", tbNo);
                head.setField("SupCode_", supCode);
                head.setField("State", StrToIntDef(status, 0));
                head.setField("VineCorp_", buff.getString("vineCorp"));
                head.setField("More", "");
                head.setField("DateFrom", dateFrom);
                head.setField("DateTo", dateTo);
            } else
            {
                app.setService("TAppTranDA.search");
                Record head = app.getDataIn().getHead();
                head.setField("TBNo_", tbNo);
                head.setField("TBDate_From", TBDate_From);
                head.setField("TBDate_To", TBDate_To);
                head.setField("Approval_", approval);

                if (!supCode.trim().isEmpty())
                    head.setField("SupCode_", supCode);
                if (!appUser.trim().isEmpty())
                    head.setField("AppUser_", appUser);
                if (!manageNo.trim().isEmpty())
                    head.setField("ManageNo_", manageNo);
                if (!status.trim().isEmpty())
                    head.setField("Status_", status);

                view.addExportFile(app.getService(), app.getExportKey());
            }

            if (!app.exec())
            {
                view.setMessage(app.getMessage());
                return view.getViewFile();
            }

            DataSet ds = app.getDataOut();
            if (corpType == 5)
            {
                while (ds.fetch())
                    ds.setField("State_", this.getState(ds.getCurrent(), buff.getString("vineCorp")));
            }
            DBGrid<TranDAH_Record> items = new DBGrid<>(ds);
            if (items.map(req, TranDAH_Record.class, this, true) == 0)
                if (req.getParameter("submit") != null)
                    view.setMessage("没有找到符合条件的数据，请重新查询！");

            double totalTOriAmount = 0.00;
            for (TranDAH_Record item : items.getList())
            {
                totalTOriAmount += item.getTOriAmount();
            }
            view.add("totalTOriAmount", totalTOriAmount);
            view.add("items", items);
            view.add("corpType", corpType);
        }
        return view.getViewFile();
    }

    /**
     * 批量更新单据状态
     * 
     * @return
     * @throws IOException
     */
    public void updatStatus()
    {
        String status = req.getParameter("status");
        String tbNo = req.getParameter("tbNo");
        Message msg = new Message();
        try (LocalService app = new LocalService(this))
        {
            app.setService("TAppTranDA.update_status");
            app.getDataIn().getHead().setField("TBNo_", tbNo);
            app.getDataIn().getHead().setField("Status_", status);
            if (!app.exec())
            {
                msg.add("status", false);
                msg.add(tbNo, "更新失败，原因：" + app.getMessage());
            } else
            {
                msg.add("status", true);
                msg.add(tbNo, msg.getStatus().get(status));
            }
        }
        try
        {
            resp.getWriter().print(msg.getJSON());
        } catch (IOException e)
        {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * 修改采购订单
     * 
     * @return
     */
    public String modify()
    {
        String tbNo = req.getParameter("tbNo");
        Shopping shop = new Shopping(this);
        if ((tbNo == null || "DA0000".equals(tbNo)))
        {
            tbNo = shop.read().getTbNo();
            if (tbNo.equals("DA0000"))
                return this.sendRedirect("TFrmTranDA");
            else
                return this.sendRedirect(String.format("TFrmTranDA.modify?tbNo=%s", tbNo));
        }
        view.setFile("pur/TFrmTranDA_modify.jsp");
        view.setExitUrl("TFrmTranDA");
        view.addLeftMenu("TPur", "进货管理");
        view.addLeftMenu("TFrmTranDA", "采购订单");

        if (req.getParameter("status") != null)
            modifyHead();

        return download();
    }

    /**
     * 获取采购单详细信息
     * 
     * @return
     */
    private String download()
    {
        try (MemoryBuffer buff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA.modify");
                TranDA_Record obj = new TranDA_Record(this))
        {
            String tbNo = getValue(buff, "tbNo");
            DataSet ds = obj.open(tbNo);

            TranDAH_Record head = new TranDAH_Record().init(ds.getHead());
            view.add("head", head);

            int status = head.getStatus();

            if (status == 0)
            {
                view.setFile("pur/TFrmTranDA_modify.jsp");
                view.setPageTitle("修改采购订单");
            } else
            {
                view.setFile("pur/TFrmTranDA_modifyRead.jsp");
                view.setPageTitle("查看采购订单");
            }
            // 获取订单单身信息
            List<TranDAB_Record> items = new ArrayList<>();
            double totalNum = 0.00;
            double totalOriAmount = 0.00;
            while (ds.fetch())
            {
                items.add(new TranDAB_Record().init(ds.getCurrent()));
                totalNum += ds.getDouble("Num_");
                totalOriAmount += ds.getDouble("OriAmount_");
            }

            view.add("totalNum", totalNum);
            view.add("totalOriAmount", totalOriAmount);
            view.add("items", items);

            buff.setField("supCode", head.getSupCode());
            buff.setField("cwCode", head.getWHCode());

            if (!buff.getString("msg").replace("{}", "").trim().isEmpty())
            {
                view.setMessage(getValue(buff, "msg"));
                buff.setField("msg", "");
            }
            
            Shopping shop = new Shopping(this);
            if (status == 0)
                shop.write("DA", tbNo, items.size());
            else
                shop.clear();
            view.add("corpType", Memory_OurInfo.getCorpType(this).ordinal());
        }

        return view.getViewFile();
    }

    /**
     * 修改单头信息，并更新状态
     */
    private void modifyHead()
    {
        view.setFile("pur/TFrmTranDA_modify.jsp");
        String tbNo = req.getParameter("tbNo");
        String status = req.getParameter("status");
        Map<String, String> map = new HashMap<>();
        map.put("-1", "作废");
        map.put("0", "撤销");
        map.put("1", "生效");

        try (MemoryBuffer buff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA.modify");
                LocalService app = new LocalService(this);
                TranDA_Record obj = new TranDA_Record(this))
        {
            DataSet ds = obj.open(tbNo);
            Record head = ds.getHead();
            String supCode = head.getString("SupCode_");

            if (supCode == null || "".equals(supCode))
                throw new RuntimeException("厂商代码不允许为空！");

            String WHCode = req.getParameter("WHCode");
            if (!head.getString("WHCode_").equals(WHCode))
            {
                head.setField("WHCode_", WHCode);
                while (ds.fetch())
                {
                    ds.setField("CWCode_", req.getParameter("WHCode"));
                }
            }
            head.setField("SupCode_", req.getParameter("supCode"));
            head.setField("SupName_", req.getParameter("supName"));
            head.setField("TBDate_", TDateTime.fromDate(req.getParameter("tbDate")).toString());
            head.setField("WHCode_", req.getParameter("WHCode"));
            head.setField("ManageNo_", req.getParameter("manageNo"));
            head.setField("PayType_", 1);
            head.setField("Remark_", req.getParameter("remark"));
            
            if (!obj.modify())
            {
                view.setMessage(obj.getMessage());
                return;
            }

            if (status.equals("0") && head.getString("Status_").equals("0"))
            {
                view.setMessage("单据保存成功！");
                return;
            }

            if (status != null)
            {
                app.setService("TAppTranDA.update_status");
                app.getDataIn().getHead().setField("TBNo_", tbNo);
                app.getDataIn().getHead().setField("Status_", status);
                if (!app.exec())
                    view.setMessage(String.format("单据%s失败，原因：%s", map.get(status), app.getMessage()));
                else
                {
                    view.setMessage(String.format("单据%s成功！", map.get(status)));
                    if ("1".equals(status))
                        // 状态更新完成发，发送邮件到客户邮箱，如果客户邮箱是空的，则不发送
                        view.setMessage(sendMailIntro(tbNo, supCode, "DA") ? "单据已生效，并发送邮件通知了该厂商"
                                : "单据已生效，但无法跟该厂商发送邮件通知！");
                }
            }

            // 更新单号到缓存
            buff.setField("tbNo", tbNo);

        } catch (Exception e)
        {
            view.setMessage(e.getMessage());
        }

    }

    /**
     * 变更单据状态后，调用邮箱
     * 
     * @return
     */
    private boolean sendMailIntro(String tbNo, String supCode, String TB)
    {
        try (LocalService appGetInfo = new LocalService(this); LocalService appSend = new LocalService(this))
        {
            appGetInfo.setService("TAppMailService.Get_InitMailInfo_Sup");
            appGetInfo.getDataIn().getHead().setField("MailType_", TB);
            appGetInfo.getDataIn().getHead().setField("MailCorp_", supCode);
            appGetInfo.getDataIn().getHead().setField("TBNo_", tbNo);

            if (!appGetInfo.exec())
                return false;

            DataSet ds = appGetInfo.getDataOut();

            if (ds.eof())
                return false;

            appSend.setService("TAppMailService.SendEmailIntro");
            appSend.getDataIn().getHead().setField("Subject_", ds.getHead().getString("Subject_"));
            appSend.getDataIn().getHead().setField("Body_", ds.getHead().getString("Body_"));

            while (ds.fetch())
            {
                appSend.getDataIn().append();
                appSend.getDataIn().setField("MailTo_",
                        String.format("%s<%s>", ds.getString("Addressee_"), ds.getString("EmailAddress_")));
            }
            return appSend.exec();
        }
    }

    /**
     * 修改采购订单单身--同时重新计算单头金额还有仓库
     * 
     * @return
     */
    public String modifyBody()
    {
        view.setFile("pur/TFrmTranDA_modifyBody.jsp");
        try (MemoryBuffer buff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA.modify");
                TranDA_Record obj = new TranDA_Record(this))
        {
            String tbNo = getValue(buff, "tbNo");
            String it = req.getParameter("it");
            String submit = req.getParameter("submit");

            DataSet dsDA = obj.open(tbNo);
            TranDAB_Record item = new TranDAB_Record();
            if (dsDA.locate("It_", it))
                view.add("body", item.init(dsDA.getCurrent()));

            if (submit != null && !"".equals(submit))
            {
                double num = StrToFloatDef(req.getParameter("num"), 0);
                if (num == 0)
                {
                    view.setMessage("请输入正确的数量，不允许为零！！");
                    return view.getViewFile();
                }

                while (dsDA.fetch())
                {
                    if (dsDA.getString("It_").equals(it))
                    {
                        dsDA.setField("ReceiveDate_", TDateTime.fromDate(req.getParameter("receiveDate")).toString());
                        dsDA.setField("Num_", num);
                        dsDA.setField("OriUP_", req.getParameter("oriUP"));
                        dsDA.setField("Discount_", req.getParameter("discount"));
                        dsDA.setField("OriAmount_", req.getParameter("oriAmount"));
                        dsDA.setField("Remark_", req.getParameter("remark"));
                        dsDA.setField("IsFree_", req.getParameter("isFree"));
                        dsDA.setField("SpareNum_", req.getParameter("spareNum"));
                        dsDA.setField("Finish_", req.getParameter("finish"));
                    }
                }

                if (!obj.modify())
                {
                    view.setMessage(obj.getMessage());
                    return view.getViewFile();
                }

                return this.sendRedirect(String.format("TFrmTranDA.getSingleBody?it=%s", it));
            }
        }

        return view.getViewFile();
    }

    /**
     * 为PC版获取详情，删除单身而生
     * 
     * @return
     */
    public String getSingleBody()
    {
        view.setFile("pur/TFrmTranDA_getSingleBody.jsp");

        try (MemoryBuffer buff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA.modify");
                TranDA_Record obj = new TranDA_Record(this))
        {
            String tbNo = getValue(buff, "tbNo");

            String submit = req.getParameter("submit");
            String it = req.getParameter("it");

            DataSet dsDA = obj.open(tbNo);

            if (submit != null && !submit.trim().isEmpty())
            {
                if (obj.delete(it))
                {
                    view.add("isDel", true);
                    view.setMessage("删除成功！");
                } else
                {
                    view.add("isDel", false);
                    view.setMessage(String.format("删除失败！原因：%s", obj.getMessage()));
                }
                return view.getViewFile();
            }

            // 获取单个商品数据
            TranDAB_Record item = new TranDAB_Record();
            if (dsDA.locate("It_", it))
                view.add("body", item.init(dsDA.getCurrent()));

            double totalNum = 0.0, totalOriAmount = 0.0;
            while (dsDA.fetch())
            {
                totalNum += dsDA.getDouble("Num_");
                totalOriAmount += dsDA.getDouble("OriAmount_");
            }

            view.add("totalNum", totalNum);
            view.add("totalOriAmount", totalOriAmount);

            return view.getViewFile();
        }

    }

    public String deleteBody()
    {
        try (MemoryBuffer buff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA.modify");
                TranDA_Record obj = new TranDA_Record(this))
        {
            String tbNo = getValue(buff, "tbNo");
            DataSet dsDA = obj.open(tbNo);
            String[] its = req.getParameterValues("it");
            if (its != null)
            {
                for (String it : its)
                {
                    if (dsDA.locate("It_", it))
                        dsDA.delete();
                }
            }
            if (obj.modify())
                buff.setField("msg", "删除成功！");
            else
                buff.setField("msg", obj.getMessage());
        }
        return this.sendRedirect("TFrmTranDA.modify");
    }

    /**
     * 采购明细变更查询
     * 
     * @return
     */
    public String purLog()
    {
        view.setFile("pur/TFrmTranDA_purLog.jsp");
        view.setExitUrl("TFrmTranDA");
        view.addLeftMenu("TPur", "进货管理");
        view.addLeftMenu("TFrmTranDA", "采购订单");
        view.setPageTitle("采购明细变更查询");

        try (MemoryBuffer buff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA.purLog");
                LocalService app = new LocalService(this))
        {
            String searchText = getValue(buff, "searchText");
            String dateFrom = getValue(buff, "dateFrom");
            String dateTo = getValue(buff, "dateTo");
            String partCode = getValue(buff, "partCode");
            String updateUserCode = getValue(buff, "updateUserCode");
            String updateUserName = getValue(buff, "updateUserName");
            String type = getValue(buff, "type");
            String tbNo = getValue(buff, "tbNo");

            if ("".equals(dateFrom))
            {
                dateFrom = TDateTime.Now().monthBof().getDate();
                buff.setField("dateFrom", dateFrom);
            }
            if ("".equals(dateTo))
            {
                dateTo = TDateTime.Now().monthEof().getDate();
                buff.setField("dateTo", dateTo);
            }

            view.add("dateFrom", dateFrom);
            view.add("dateTo", dateTo);
            view.add("updateUserName", updateUserName);

            String TBDate_From = TDateTime.fromDate(dateFrom).toString();
            String TBDate_To = TDateTime.fromDate(dateTo).addDay(1).toString();

            if ("".equals(tbNo))
                tbNo = "DA*";
            if ("".equals(type))
                type = "0";

            app.setService("TAppTranDA.search_PurLog");
            app.getDataIn().getHead().setField("TBDate_From", TBDate_From);
            app.getDataIn().getHead().setField("TBDate_To", TBDate_To);
            app.getDataIn().getHead().setField("Type_", type);
            app.getDataIn().getHead().setField("TBNo_", tbNo);

            if (!"".equals(searchText))
                app.getDataIn().getHead().setField("SearchText_", searchText);
            if (!"".equals(partCode))
                app.getDataIn().getHead().setField("PartCode_", partCode);
            if (!"".equals(updateUserCode))
                app.getDataIn().getHead().setField("AppUser_", updateUserCode);

            if (!app.exec())
            {
                view.setMessage(app.getMessage());
                return view.getViewFile();
            }

            DBGrid<TranDAH_Record> items = new DBGrid<>(app.getDataOut());
            if (items.map(req, TranDAH_Record.class, this, true) == 0)
                if (req.getParameter("submit") != null)
                    view.setMessage("没有找到符合条件的数据，请重新查询！");
            view.add("items", items);
        }

        return view.getViewFile();
    }

    /**
     * 添加采购订单-->第一步：选择厂商
     * 
     * @return
     */
    public String appendStep1()
    {
        try (MemoryBuffer buff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA.appendHead"))
        {
            String localWH = getValue(buff, "localWH");
            buff.setField("localWH", localWH);
        }

        try (MemoryBuffer buff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmSupInfo"))
        {
            buff.setField("selectTarget", "TFrmTranDA.appendHead");
            buff.setField("proirPage", "TFrmTranDA");
            buff.setField("selectTitle", "第一步：选择厂商");
        }
        return this.sendRedirect("TFrmSupInfo.select");
    }

    /**
     * 添加单头
     * 
     * @return
     */
    public String appendHead()
    {
        try (MemoryBuffer buff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA.appendHead");
                MemoryBuffer modifyBuff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(),
                        "TFrmTranDA.modify");
                LocalService app = new LocalService(this);
                AppBean bean = new AppBean(sess))
        {
            String supCode = getValue(buff, "code");
            String localWH = getValue(buff, "localWH");
            String whCode = "".equals(localWH) ? CorpOptions.getDefaultCWCode(bean) : localWH;

            app.setService("TAppTranDA.append");
            Record head = app.getDataIn().getHead();
            head.setField("WHCode_", whCode);
            head.setField("RecCode_", supCode);
            head.setField("SupCode_", supCode);
            head.setField("PayType_", 1);
            head.setField("Currency_", "CNY");
            head.setField("ExRate_", 1);
            head.setField("Tax_", 0);
            head.setField("Status_", 0);
            head.setField("SalesCode_", sess.getUserCode());
            head.setField("TBDate_", TDate.Today());
            head.setField("Final_", false);
            head.setField("IsReturn_", false);

            if (!app.exec())
            {
                view.setExitUrl("TFrmTranDA");
                return this.errorPage(app.getMessage());
            }

            String tbNo = app.getDataOut().getHead().getString("TBNo_");

            Shopping shop = new Shopping(this);
            Shopping_Record sr = shop.read();
            if (sr != null && "DA0000".equals(sr.getTbNo()))
            {
                shop.write("DA", tbNo, 0);
                modifyBuff.setField("tbNo", tbNo);
                return null;
            } else
                return this.sendRedirect(String.format("TFrmTranDA.modify?tbNo=%s", tbNo));
        }
    }

    /**
     * 选择添加商品页面
     * 
     * @return
     */
    public String selectProduct()
    {
        view.setFile("pur/TFrmTranDA_selectProduct.jsp");
        view.setExitUrl("TFrmTranDA.modify");
        view.addLeftMenu("TPur", "进货管理");
        view.addLeftMenu("TFrmTranDA", "采购订单");
        view.addLeftMenu("TFrmTranDA.modify", "修改采购订单");
        view.setPageTitle("从本地库存添加明细");

        Map<String, Object> map = new HashMap<>();
        map.put("WarnNum_Zero_true", true);
        map.put("WarnNum_Zero_false", false);
        map.put("WarnNum_Stock_true", true);
        map.put("WarnNum_Stock_false", false);
        map.put("WarnNum_Zero_true_field", "WarnNum_Zero");
        map.put("WarnNum_Zero_false_field", "WarnNum_Zero");
        map.put("WarnNum_Stock_true_field", "WarnNum_Stock");
        map.put("WarnNum_Stock_false_field", "WarnNum_Stock");

        try (MemoryBuffer buff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(),
                "TFrmTranDA.selectProduct");
                MemoryBuffer buffModify = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(),
                        "TFrmTranDA.modify");
                LocalService app = new LocalService(this);
                TranDA_Record obj = new TranDA_Record(this);
                PartClass_Record part = new PartClass_Record(this))
        {
            String tbNo = getValue(buffModify, "tbNo");

            if (tbNo.trim().isEmpty())
            {
                view.setMessage("缓存出错，找不到要添加的采购单号！");
                view.setExitUrl("TFrmTranDA");
                return view.getViewFile();
            }

            Record headDA = obj.open(tbNo).getHead();

            String supCode = headDA.getString("SupCode_");
            String brand = getValue(buff, "brand");
            String searchText = getValue(buff, "searchText");
            String stock = getValue(buff, "stock");
            String dateFrom = getValue(buff, "dateFrom");
            String dateTo = getValue(buff, "dateTo");
            int maxRecord = StrToIntDef(getValue(buff, "maxRecord"), 100);
            String[] partClass = getValue(buff, "partClass").split("->");

            view.add("supCode", supCode);
            view.add("tbNo", buffModify.getString("tbNo"));
            view.add("brandList", part.getBrand());

            if (dateFrom.trim().isEmpty())
            {
                dateFrom = TDateTime.Now().monthBof().getDate();
                buff.setField("dateFrom", dateFrom);
            }
            if (dateTo.trim().isEmpty())
            {
                dateTo = TDateTime.Now().monthEof().getDate();
                buff.setField("dateTo", dateTo);
            }

            String Sale_BeginDate = TDateTime.fromDate(dateFrom).toString();
            String Sale_EndDate = TDateTime.fromDate(dateTo).toString();

            view.add("class1", partClass[0]);
            view.add("maxRecord", maxRecord);
            view.add("dateFrom", dateFrom);
            view.add("dateTo", dateTo);

            String submit = req.getParameter("submit");
            String pageno = req.getParameter("pageno");

            if ((submit != null && !submit.trim().isEmpty()) || (pageno != null && !pageno.trim().isEmpty()))
            {
                app.setService("TAppStockBalance.Search");
                Record head = app.getDataIn().getHead();
                if (!searchText.trim().isEmpty())
                    head.setField("SearchText_", searchText);
                if (!stock.trim().isEmpty())
                    head.setField(map.get(stock + "_field").toString(), map.get(stock));
                if (!dateFrom.trim().isEmpty())
                    head.setField("Sale_BeginDate", Sale_BeginDate);
                if (!dateFrom.trim().isEmpty())
                    head.setField("Sale_EndDate", Sale_EndDate);

                head.setField("SupCode_", supCode);
                head.setField("Brand_", brand);
                head.setField("MaxRecord_", maxRecord);

                if (partClass.length > 0)
                    head.setField("Class1_", partClass[0]);
                if (partClass.length > 1)
                    head.setField("Class2_", partClass[1]);
                if (partClass.length > 2)
                    head.setField("Class3_", partClass[2]);

                if (!app.exec())
                {
                    view.setMessage(app.getMessage());
                    return view.getViewFile();
                }

                DataSet ds = app.getDataOut();
                DBGrid<Product_Record> items = new DBGrid<>(ds);
                if (items.map(req, Product_Record.class, this, true) == 0)
                    view.setMessage("找不到符合条件的数据，请重新查询！");

                view.add("items", items);
            }
        }

        return view.getViewFile();
    }

    /**
     * 添加DA单单身信息
     * 
     * @return
     * @throws IOException
     */
    public String appendBody() throws IOException
    {
        Map<String, Object> map = new HashMap<>();
        try (MemoryBuffer buff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA.modify");
                TranDA_Record obj = new TranDA_Record(this);
                LocalService app1 = new LocalService(this);
                LocalService app2 = new LocalService(this))
        {
            double num = (Double) req.getAttribute("num");
            String[] products = (String[]) req.getAttribute("products") ;
            
            String spTBNo = req.getParameter("spTBNo");
            String vineCorp = (String) req.getSession().getAttribute("vineCorp");
            String tbNo = getValue(buff, "tbNo");
            DataSet dsDA = obj.open(tbNo);
            Record headDA = dsDA.getHead();
            String supCode = headDA.getString("SupCode_");
            Map<String, String> ownCodes = new HashMap<>();
            //添加上游商品资料导到本地商品资料
            if(products != null && products.length > 0 && products[0].indexOf("|") > -1)
                ownCodes = appendSupToSelf(products, supCode);
            
            String[] sourceFields = { "Code_", "PartCode_", "Desc_", "Spec_", "Unit_", "Unit1_", "Rate1_", "GoodUP_",
                    "Discount_", "OriUP_", "UPControl_" };
            String[] targetFields = { "PartCode_", "SupPart_", "Desc_", "Spec_", "Unit_", "Unit1_", "Rate1_",
                    "GoodUP_", "Discount_", "OriUP_", "UPControl_" };

            for (String item : products)
            {
                String[] items = item.split("\\|");
                String partCode = "";
                String code = "";
                if(ownCodes.size() > 0)
                {
                    if(ownCodes.get(items[0]) != null)
                        partCode = ownCodes.get(items[0]);
                    else
                        partCode = items[1];
                    code = items[0];
                }else
                    partCode = items[0];
                // 处理促销包
                double oriup = 0;
                if (spTBNo != null && !spTBNo.equals(""))
                {
                    // 取促销价
                    app2.setService("TAppTranSP.Download");
                    Record head = app2.getDataIn().getHead();
                    head.setField("CorpNo_", vineCorp);
                    head.setField("TBNo_", spTBNo);
                    head.setField("Promotion", "");
                    if (!app2.exec())
                        throw new RuntimeException(app2.getMessage());
                    DataSet ds = app2.getDataOut();
                    if (ds.locate("PartCode_", code))
                        oriup = ds.getDouble("OriUP_");
                    
                }
                
                app1.setService("TAppStockBalance.Search");
                app1.getDataIn().getHead().setField("SupCode_", supCode);
                app1.getDataIn().getHead().setField("PartCode_", partCode);

                if (!app1.exec())
                    throw new RuntimeException(app1.getMessage());

                DataSet dataSource = app1.getDataOut();
                if (dataSource.eof())
                    throw new RuntimeException("没有查到任何数据");

                if (dsDA.locate("PartCode_", partCode))
                {
                    dsDA.setField("Num_", dsDA.getDouble("Num_") + num);
                } else
                {
                    dsDA.append();
                    dsDA.copyRecord(dataSource.getCurrent(), sourceFields, targetFields);

                    dsDA.setField("It_", dsDA.getRecNo());
                    dsDA.setField("TBNo_", tbNo);
                    dsDA.setField("CWCode_", headDA.getString("WHCode_"));
                    dsDA.setField("Num_", num);
                    dsDA.setField("SpareNum_", 0);
                    dsDA.setField("Approval_", true);
                    dsDA.setField("ReceiveDate_", TDate.Today().addDay(3));
                    dsDA.setField("IsFree_", dsDA.getDouble("SpareNum_") > 0 ? true : false);
                    dsDA.setField("Final_", false);
                }
                if(oriup != 0)
                    dsDA.setField("OriUP_", oriup);
                dsDA.setField("OriAmount_",
                        dsDA.getBoolean("IsFree_") ? 0 : dsDA.getDouble("Num_") * dsDA.getDouble("OriUP_"));
                if (dsDA.getDouble("Rate1_") != 0)
                    dsDA.setField("Num1_", dsDA.getDouble("Num_") / dsDA.getDouble("Rate1_"));
                else
                    dsDA.setField("Num1_", 0);
            }
            if (!obj.modify())
            {
                buff.setField("msg", obj.getMessage());
                return this.sendRedirect("TFrmTranDA.modify");
            }

            Shopping shop = new Shopping(this);
            shop.updateNum(dsDA.size());

            map.put("error", false);
            map.put("msg", "添加成功！");
            map.put("num", dsDA.size());
            (new Shopping(this)).updateNum(dsDA.size());
            resp.getWriter().print(JSONObject.fromObject(map));
        }catch (Exception e) {
            map.put("msg", e.getMessage());
            resp.getWriter().print(JSONObject.fromObject(map));
        }
        return view.getViewFile();
    }

    /**
     * 从上游商品中导入商品明细
     * 
     * @return
     */
    public String proProduct()
    {
        try (MemoryBuffer buff1 = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmProSearch");
                MemoryBuffer buff2 = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA.modify"))
        {
            for (String key : buff1.getRecord().getFieldDefs().getFields())
                buff1.setField(key, null);
            session.setAttribute("EasyDefaultBook", this.getValue(buff2, "supCode"));
            TFrmSupFirst item = new TFrmSupFirst();
            item.init(this);
            item.getVineCorp(this.getValue(buff2, "supCode"));
            buff1.setField("supCode", this.getValue(buff2, "supCode"));
            buff1.setField("cwCode", this.getValue(buff2, "cwCode"));
            buff1.setField("proirPage", req.getHeader("Referer"));
            return this.sendRedirect("TFrmProSearch.select");
        }
    }

    /**
     * 导入上游商品到单身
     * 
     * @return
     * @throws IOException
     */
    public String proToDA() throws IOException
    {
        try (MemoryBuffer buff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA.modify");
                TranDA_Record obj = new TranDA_Record(this);
                LocalService app1 = new LocalService(this);
                LocalService app2 = new LocalService(this))
        {
            Map<String, Object> map = new HashMap<>();
            double num = 1;
            if (req.getParameter("num") != null)
                num = Double.parseDouble(req.getParameter("num"));
            String vineCorp = (String) req.getSession().getAttribute("vineCorp");
            String tbNo = getValue(buff, "tbNo");
            String spTBNo = req.getParameter("spTBNo");
            String[] products = req.getParameterValues("products");
            DataSet dsDA = obj.open(tbNo);
            Record headDA = dsDA.getHead();
            String supCode = headDA.getString("SupCode_");

            appendSupToSelf(products, supCode);

            StringBuilder strBuff = new StringBuilder();
            String[] sourceFields = { "Code_", "Desc_", "Spec_", "Unit_", "GoodUP_", "Discount_", "OriUP_", "Unit1_",
                    "Rate1_", "UPControl_", };
            String[] targetFields = { "PartCode_", "Desc_", "Spec_", "Unit_", "GoodUP_", "Discount_", "OriUP_",
                    "Unit1_", "Rate1_", "UPControl_", };

            for (String item : products)
            {
                String[] items = item.split("\\|");
                String supPart = items[0];
                String partCode = "";
                if (items.length == 2)
                    partCode = items[1];

                // 处理促销包
                boolean premiums = false;
                double oriup = 0;
                if (spTBNo != null && !spTBNo.equals(""))
                {
                    // 取促销价
                    app2.setService("TAppTranSP.Download");
                    Record head = app2.getDataIn().getHead();
                    head.setField("CorpNo_", vineCorp);
                    head.setField("TBNo_", spTBNo);
                    head.setField("Promotion", "");
                    if (!app2.exec())
                        strBuff.append(app2.getMessage());
                    DataSet ds = app2.getDataOut();
                    if (ds.locate("PartCode_", supPart))
                    {
                        if (ds.getBoolean("IsSpare_"))
                            premiums = true;
                        oriup = ds.getDouble("OriUP_");
                    }
                }

                app1.setService("TAppCusShareBrand.GetSupPartList");
                app1.getDataIn().getHead().setField("SupCode_", supCode);
                app1.getDataIn().getHead().setField("SupPart_", supPart);

                if (!app1.exec())
                {
                    map.put("msg", app1.getMessage());
                    resp.getWriter().print(JSONObject.fromObject(map));
                    return null;
                }

                DataSet dataSource = app1.getDataOut();
                // 已存在单身中的料号则不添加
                if (dsDA.locate("PartCode_", partCode))
                {
                    dsDA.setField("Num_", dsDA.getDouble("Num_") + num);
                } else
                {
                    dsDA.append();
                    dsDA.copyRecord(dataSource.getCurrent(), sourceFields, targetFields);

                    dsDA.setField("It_", dsDA.getRecNo());
                    dsDA.setField("TBNo_", tbNo);
                    dsDA.setField("CWCode_", headDA.getString("WHCode_"));
                    dsDA.setField("Num_", num);
                    dsDA.setField("SpareNum_", premiums ? num : 0);
                    dsDA.setField("Approval_", true);
                    dsDA.setField("ReceiveDate_", TDate.Today().addDay(3));
                    dsDA.setField("IsFree_", dsDA.getDouble("SpareNum_") > 0 ? true : false);
                    dsDA.setField("Final_", false);
                }
                if (oriup != 0)
                    dsDA.setField("OriUP_", oriup);
                dsDA.setField("OriAmount_",
                        dsDA.getBoolean("IsFree_") ? 0 : dsDA.getDouble("Num_") * dsDA.getDouble("OriUP_"));
                if (dsDA.getDouble("Rate1_") != 0)
                    dsDA.setField("Num1_", dsDA.getDouble("Num_") / dsDA.getDouble("Rate1_"));
                else
                    dsDA.setField("Num1_", 0);
            }

            if (!obj.modify())
            {
                map.put("msg", obj.getMessage());
                resp.getWriter().print(JSONObject.fromObject(map));
                return null;
            }

            if (strBuff.length() > 0)
                map.put("msg", strBuff.toString());
            else
                map.put("msg", "添加成功！");
            map.put("num", dsDA.size());
            (new Shopping(this)).updateNum(dsDA.size());
            resp.getWriter().print(JSONObject.fromObject(map));
            return null;
        }
    }

    private Map<String, String> appendSupToSelf(String[] products, String supCode)
    {
        Map<String, String> ownCodes = new HashMap<>();
        try (LocalService app1 = new LocalService(this); LocalService app2 = new LocalService(this))
        {
            app1.setService("TAppPartInfo.AppendFromSup");
            app1.getDataIn().getHead().setField("SupCode_", supCode);
            DataSet dsIn = app1.getDataIn();

            // 处理上游商品未导入本地时，先导入本地
            for (String item : products)
            {
                String supPart = item.split("\\|")[0];
                app2.setService("TAppPartStock.Search_PartCode");
                app2.getDataIn().getHead().setField("SupCode_", supCode);
                app2.getDataIn().getHead().setField("Code_", supPart);
                if (!app2.exec())
                    throw new RuntimeException(app2.getMessage());

                DataSet dsCus = app2.getDataOut();
                if (!dsCus.eof())
                {
                    ownCodes.put(supPart, dsCus.getString("CusPart_"));
                }else
                {
                    dsIn.append();
                    dsIn.setField("Code_", supPart);
                }
            }
            // 添加上游商品到本地库存中
            if (!dsIn.eof())
            {
                if (!app1.exec())
                    throw new RuntimeException(app1.getMessage());
            }
            DataSet ds = app1.getDataOut();
            while (ds.fetch())
                ownCodes.put(ds.getString("SupPart_"), ds.getString("PartCode_"));
        }
        return ownCodes;
    }

    /**
     * 从拆零商品中导入商品明细
     * 
     * @return
     */
    public String clProduct()
    {
        view.setFile("pur/TFrmTranDA_clProduct.jsp");
        view.setExitUrl("TFrmTranDA.modify");
        view.addLeftMenu("TPur", "进货管理");
        view.addLeftMenu("TFrmTranDA", "采购订单");
        view.addLeftMenu("TFrmTranDA.modify", "修改采购订单");
        view.setPageTitle("从拆零商品添加明细");

        try (MemoryBuffer buffCL = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA.clProduct");
                MemoryBuffer buffModify = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(),
                        "TFrmTranDA.modify"); LocalService app = new LocalService(this))
        {
            String searchText = getValue(buffCL, "searchText");
            String dateFrom = getValue(buffCL, "dateFrom");
            String dateTo = getValue(buffCL, "dateTo");
            String tbNo = getValue(buffCL, "tbNo");

            if (tbNo.trim().isEmpty())
                tbNo = "CL*";

            if (dateFrom.trim().isEmpty())
            {
                dateFrom = TDateTime.Now().monthBof().getDate().toString();
                buffCL.setField("dateFrom", dateFrom);
            }
            if (dateTo.trim().isEmpty())
            {
                dateTo = TDateTime.Now().getDate().toString();
                buffCL.setField("dateTo", dateTo);
            }

            String TBDate_From = TDateTime.fromDate(dateFrom).toString();
            String TBDate_To = TDateTime.fromDate(dateTo).addDay(1).toString();

            view.add("dateFrom", dateFrom);
            view.add("dateTo", dateTo);
            view.add("tbNo", tbNo);
            view.setMessage(buffCL.getString("msg"));
            buffCL.setField("msg", "");

            app.setService("TAppCLProduct.Search");
            Record head = app.getDataIn().getHead();
            head.setField("TBDate_From", TBDate_From);
            head.setField("TBDate_To", TBDate_To);
            head.setField("TBNo_", tbNo);
            head.setField("IsReturn_", false);

            if (!searchText.trim().isEmpty())
                head.setField("SearchText_", searchText);

            if (!app.exec())
            {
                view.setMessage(app.getMessage());
                return view.getViewFile();
            }

            DataSet ds = app.getDataOut();
            DBGrid<CLProduct_Record> items = new DBGrid<>(ds);
            if (items.map(req, CLProduct_Record.class, this, true) == 0)
                if (req.getParameter("submit") != null)
                    view.setMessage("没有找到符合条件的数据，请重新查找！");
            view.add("items", items);
        }
        return view.getViewFile();
    }

    /**
     * 拆零商品添加到采购订单
     * 
     * @return
     * @throws IOException
     */
    public String clAppendToDA() throws IOException
    {
        try (MemoryBuffer buffCL = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA.clProduct");
                MemoryBuffer buffModify = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(),
                        "TFrmTranDA.modify");
                TranDA_Record obj = new TranDA_Record(this);
                LocalService app = new LocalService(this))
        {
            Map<String, Object> map = new HashMap<>();
            String[] tbNos = req.getParameterValues("products");
            if (tbNos == null || tbNos.length < 0)
            {
                map.put("msg", "请选择要更新的返厂商品！");
                resp.getWriter().print(JSONObject.fromObject(map));
                return null;
            }

            String tbNoDA = buffModify.getString("tbNo");
            DataSet dsDA = obj.open(tbNoDA);
            Record headDA = dsDA.getHead();

            String[] sourceFields = { "PartCode_", "Desc_", "Spec_", "Unit_", "GoodUP_", "OriUP_", "OriAmount_",
                    "Num_", "Remark_" };
            String[] targetFields = { "PartCode_", "Desc_", "Spec_", "Unit_", "GoodUP_", "OriUP_", "OriAmount_",
                    "Num_", "Remark_" };

            List<String> listCL = new ArrayList<>();
            StringBuilder strBuff = new StringBuilder();
            for (String tbNo : tbNos)
            {
                app.setService("TAppCLProduct.Search");
                app.getDataIn().getHead().setField("IsReturn_", false);
                app.getDataIn().getHead().setField("TBNo_", tbNo);

                if (!app.exec())
                    throw new RuntimeException(app.getMessage());

                DataSet dsCL = app.getDataOut();

                if (dsDA.fetch() && dsDA.locate("PartCode_;", dsCL.getString("PartCode_")))
                {
                    String desc = dsCL.getString("Desc_");
                    String spec = dsCL.getString("Spec_");
                    if (!spec.trim().isEmpty())
                        desc += "，" + spec;
                    strBuff.append(String.format("%s 已存在列表中，无需重复添加！<br>", desc));
                    continue;
                }

                dsDA.append();
                dsDA.copyRecord(dsCL.getCurrent(), sourceFields, targetFields);

                dsDA.setField("TBNo_", tbNoDA);
                dsDA.setField("It_", dsDA.getRecNo());
                dsDA.setField("CWCode_", headDA.getString("WHCode_"));
                dsDA.setField("SpareNum_", 0);
                dsDA.setField("Approval_", true);
                dsDA.setField("ReceiveDate_", TDate.Today().addDay(3));
                dsDA.setField("IsFree_", dsDA.getDouble("SpareNum_") > 0 ? true : false);
                dsDA.setField("OriAmount_",
                        dsDA.getBoolean("IsFree_") ? 0 : dsDA.getDouble("Num_") * dsDA.getDouble("OriUP_"));

                // FIXME
                // CL单没有存Rate1字段，Delphi设置成1，没有关联PartInfo表查询，是否需要增加一个importCLToDA的服务，同时是否需要在DA单单身增加CL单的单号，生效再更新CL单的返厂状态
                // 2015/12/26 黄荣君
                dsDA.setField("Rate1_", dsDA.getDouble("Rate1_") == 0 ? 1 : dsDA.getDouble("Rate1_"));
                dsDA.setField("Num1_", dsDA.getDouble("Num_") / dsDA.getDouble("Rate1_"));
                dsDA.setField("Final_", false);
                listCL.add(tbNo);
            }

            if (!obj.modify())
            {
                map.put("msg", obj.getMessage());
                resp.getWriter().print(JSONObject.fromObject(map));
                return null;
            }
            if (strBuff.length() > 0)
                map.put("msg", strBuff.toString());
            else
                map.put("msg", "添加成功！");
            map.put("num", dsDA.size());
            (new Shopping(this)).updateNum(dsDA.size());
            resp.getWriter().print(JSONObject.fromObject(map));
            // 4、更新拆零单号的返厂信息
            updateIsReturn(listCL);
        }

        return null;
    }

    /**
     * 更新拆零商品的返厂状态
     * 
     * @return
     */
    private void updateIsReturn(List<String> tbNos)
    {
        try (MemoryBuffer buff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA.modify");
                LocalService app = new LocalService(this))
        {
            app.setService("TAppCLProduct.UpdateIsReturn");
            DataSet dataIn = app.getDataIn();
            for (String tbNo : tbNos)
            {
                dataIn.append();
                dataIn.setField("TBNo_", tbNo);
            }

            if (!app.exec())
                buff.setField("msg", app.getMessage());
        }
    }

    // 取得应付账款
    private double getAP(String book)
    {
        double price = 0.0;
        try (LocalService app = new LocalService(this))
        {
            price = 0.0;
            app.setService("TAppTranAP.GetTotal");
            app.getDataIn().getHead().setField("SupCode_", book);
            if (!app.exec())
                throw new RuntimeException(app.getMessage());
            DataSet ds = app.getDataOut();
            if (!ds.eof())
                price = ds.getDouble("Total0");
        }
        return price;
    }

    // 取得订单状态
    private int getState(Record ds, String vineCorp)
    {
        if (ds.getInt("Finish_") > 0)
            return 4;

        if (!ds.getBoolean("IsReturn_"))
            return 0;

        int state = 0;
        try (LocalService app = new LocalService(this))
        {
            app.setService("TAppEasy.getSupTBNoState");
            Record head = app.getDataIn().getHead();
            head.setField("TBNo_", ds.getString("TBNo_"));
            head.setField("VineCorp_", vineCorp);
            if (!app.exec())
                throw new RuntimeException(app.getMessage());
            DataSet ds1 = app.getDataOut();
            if (ds1.eof())
                state = 0;
            else
            {
                while (ds1.fetch())
                {
                    if ((ds1.getInt("Finish_") == 0 || ds1.getInt("Finish_") == 1) && ds1.getInt("Process_") == 1)
                        state = 3;
                    else if (ds1.getInt("Finish_") == 1 && ds1.getInt("Process_") == 2)
                        state = 2;
                    else
                        state = 1;
                }
            }
        }
        return state;
    }

    public void setTBNo(String tbNo)
    {
        try (MemoryBuffer buff = new MemoryBuffer(BufferType.getUserForm, sess.getUserCode(), "TFrmTranDA.modify"))
        {
            buff.setField("tbNo", tbNo);
        }
    }

    /**
     * 分页显示采购订单数据
     * 
     * @param obj
     * @param row
     */
    @Override
    public void build(Object obj, Record row)
    {
        if (obj instanceof TranDAH_Record)
        {
            TranDAH_Record item = (TranDAH_Record) obj;
            item.init(row);
        } else if (obj instanceof TranDAB_Record)
        {
            TranDAB_Record item = (TranDAB_Record) obj;
            item.init(row);
        } else if (obj instanceof Product_Record)
        {
            Product_Record item = (Product_Record) obj;
            item.init(row);
        } else if (obj instanceof CLProduct_Record)
        {
            CLProduct_Record item = (CLProduct_Record) obj;
            item.init(row);
        }
    }

	public static void main(String[] args)
	{
		System.out.println("GitHub");
	}
	
}
